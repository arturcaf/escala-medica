package com.escala;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Escala Medica v5
 * - SQLite para persistencia real
 * - SSE (Server-Sent Events) para atualizacoes em tempo real
 * - Login Admin (Dr. Artur) com acesso total
 * - Quando admin edita qualquer coisa, TODOS os usuarios conectados
 *   recebem a atualizacao instantaneamente sem recarregar a pagina
 */
public class EscalaServer {

    // ── CONFIG ──────────────────────────────────────────────
    static final int PORT = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;
    static final double HOSP_LAT = -12.7010338;
    static final double HOSP_LON = -38.3324179;
    static final double CHECKIN_RADIUS_KM = 2.0;
    static final String ADMIN_CRM = "27140";
    static final String ADMIN_SENHA = "71991402300";
    static final String ADMIN_NOME = "Artur Cesar";

    // ── STATE ───────────────────────────────────────────────
    static List<String[]> escala = new ArrayList<>();
    static List<String[]> medicos = new ArrayList<>();
    static Connection db;
    static Path dataDir;
    static String frontendHtml = "";
    static Map<String, Map<String,String>> sessions = new ConcurrentHashMap<>();
    static AtomicInteger trocaCounter = new AtomicInteger(1);

    // SSE: lista de clientes conectados para push em tempo real
    static final List<HttpExchange> sseClients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws Exception {
        System.out.println("\n  ╔══════════════════════════════════════════╗");
        System.out.println("  ║   ESCALA MEDICA v5 - Tempo Real + SQLite ║");
        System.out.println("  ║   Admin edita → todos atualizam na hora  ║");
        System.out.println("  ╚══════════════════════════════════════════╝\n");

        String jarPath = EscalaServer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        Path jarDir = Paths.get(jarPath).getParent();
        dataDir = jarDir.resolve("data");
        if (!Files.exists(dataDir)) dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

        initDatabase();
        loadEscala();
        loadMedicos();
        loadFrontend(jarDir);

        System.out.println("  Escala: " + escala.size() + " plantoes");
        System.out.println("  Medicos: " + medicos.size() + " cadastrados");
        System.out.println("  Banco: " + dataDir.resolve("escala.db").toAbsolutePath());

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/login", EscalaServer::handleLogin);
        server.createContext("/api/logout", EscalaServer::handleLogout);
        server.createContext("/api/me", EscalaServer::handleMe);
        server.createContext("/api/escala", EscalaServer::handleEscala);
        server.createContext("/api/medicos", EscalaServer::handleMedicos);
        server.createContext("/api/trocas", EscalaServer::handleTrocas);
        server.createContext("/api/checkin", EscalaServer::handleCheckin);
        server.createContext("/api/notificacoes", EscalaServer::handleNotificacoes);
        server.createContext("/api/stats", EscalaServer::handleStats);
        server.createContext("/api/events", EscalaServer::handleSSE);  // SSE endpoint
        server.createContext("/", EscalaServer::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("\n  Servidor: http://localhost:" + PORT);
        System.out.println("  Admin: CRM " + ADMIN_CRM + " | Senha: " + ADMIN_SENHA);
        System.out.println("  SSE: /api/events (atualizacoes em tempo real)\n");
    }

    // ── DATABASE ─────────────────────────────────────────────
    static void initDatabase() throws Exception {
        Path dbPath = dataDir.resolve("escala.db");
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        db.setAutoCommit(true);
        Statement st = db.createStatement();
        st.execute("CREATE TABLE IF NOT EXISTS trocas (id TEXT PRIMARY KEY, plantao_id TEXT, data_plantao TEXT, dia_plantao TEXT, turno TEXT, plantonista_original TEXT, solicitante TEXT, substituto TEXT, motivo TEXT, obs TEXT DEFAULT '', status TEXT DEFAULT 'PENDENTE', data_solicitacao TEXT, obs_gestor TEXT DEFAULT '', data_decisao TEXT DEFAULT '')");
        st.execute("CREATE TABLE IF NOT EXISTS checkins (id TEXT PRIMARY KEY, tipo TEXT, medico TEXT, plantao_id TEXT DEFAULT '', lat TEXT, lon TEXT, distancia_km TEXT, data TEXT)");
        st.execute("CREATE TABLE IF NOT EXISTS notificacoes (id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT, titulo TEXT, mensagem TEXT, data TEXT, lida INTEGER DEFAULT 0)");
        st.execute("CREATE TABLE IF NOT EXISTS escala_overrides (plantao_id TEXT PRIMARY KEY, plantonista TEXT, status TEXT, turno TEXT, updated_at TEXT)");
        st.close();
        ResultSet rs = db.createStatement().executeQuery("SELECT MAX(CAST(REPLACE(id,'TRK-','') AS INTEGER)) FROM trocas");
        if (rs.next() && rs.getObject(1) != null) trocaCounter.set(rs.getInt(1) + 1);
        rs.close();
        System.out.println("  Banco SQLite: " + dbPath.getFileName());
    }

    static void loadEscala() throws Exception {
        Path f = dataDir.resolve("escala.json");
        if (!Files.exists(f)) { try (InputStream is = EscalaServer.class.getResourceAsStream("/data/escala.json")) { if (is != null) Files.copy(is, f); } }
        if (Files.exists(f)) {
            escala = parseJsonArrayOfArrays(Files.readString(f));
            ResultSet rs = db.createStatement().executeQuery("SELECT * FROM escala_overrides");
            while (rs.next()) {
                String pid = rs.getString("plantao_id");
                for (String[] p : escala) {
                    if (p[0].equals(pid)) {
                        if (rs.getString("plantonista") != null) p[5] = rs.getString("plantonista");
                        if (rs.getString("status") != null) p[6] = rs.getString("status");
                        if (rs.getString("turno") != null) p[3] = rs.getString("turno");
                        break;
                    }
                }
            }
            rs.close();
        }
    }

    static void loadMedicos() throws Exception {
        Path f = dataDir.resolve("medicos.json");
        if (!Files.exists(f)) { try (InputStream is = EscalaServer.class.getResourceAsStream("/data/medicos.json")) { if (is != null) Files.copy(is, f); } }
        if (Files.exists(f)) medicos = parseJsonArrayOfArrays(Files.readString(f));
    }

    static void loadFrontend(Path jarDir) throws Exception {
        Path htmlFile = jarDir.resolve("index.html");
        if (Files.exists(htmlFile)) { frontendHtml = Files.readString(htmlFile); return; }
        try (InputStream is = EscalaServer.class.getResourceAsStream("/static/index.html")) {
            if (is != null) { frontendHtml = new String(is.readAllBytes(), StandardCharsets.UTF_8); return; }
        }
        frontendHtml = "<html><body><h1>index.html nao encontrado</h1></body></html>";
    }

    // ── SSE - SERVER-SENT EVENTS ─────────────────────────────
    // Quando admin faz qualquer mudanca, todos os clientes recebem push instantaneo
    static void handleSSE(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("Connection", "keep-alive");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, 0);

        sseClients.add(ex);
        System.out.println("  SSE: cliente conectado (total: " + sseClients.size() + ")");

        // Send initial ping
        try {
            OutputStream os = ex.getResponseBody();
            os.write("data: {\"type\":\"connected\"}\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            sseClients.remove(ex);
        }
    }

    // Broadcast event to ALL connected clients
    static void broadcast(String eventType, String data) {
        String msg = "data: {\"type\":\"" + eventType + "\",\"data\":" + data + ",\"ts\":\"" + now() + "\"}\n\n";
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        List<HttpExchange> dead = new ArrayList<>();
        synchronized (sseClients) {
            for (HttpExchange client : sseClients) {
                try {
                    client.getResponseBody().write(bytes);
                    client.getResponseBody().flush();
                } catch (Exception e) {
                    dead.add(client);
                }
            }
        }
        sseClients.removeAll(dead);
        if (!dead.isEmpty()) System.out.println("  SSE: " + dead.size() + " clientes desconectados, " + sseClients.size() + " ativos");
    }

    // ── NOTIFICATION + BROADCAST ─────────────────────────────
    static void addNotificacaoAndBroadcast(String tipo, String titulo, String mensagem) {
        try {
            PreparedStatement ps = db.prepareStatement("INSERT INTO notificacoes (tipo, titulo, mensagem, data, lida) VALUES (?,?,?,?,0)");
            ps.setString(1, tipo); ps.setString(2, titulo); ps.setString(3, mensagem); ps.setString(4, now());
            ps.executeUpdate(); ps.close();
        } catch (Exception e) { System.err.println("Erro notif: " + e.getMessage()); }

        // Broadcast to all SSE clients
        String notifJson = "{\"tipo\":\"" + escapeJson(tipo) + "\",\"titulo\":\"" + escapeJson(titulo) + "\",\"mensagem\":\"" + escapeJson(mensagem) + "\",\"data\":\"" + now() + "\"}";
        broadcast("notificacao", notifJson);
    }

    // ── SESSION ──────────────────────────────────────────────
    static String getToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) for (String p : cookie.split(";")) { p=p.trim(); if (p.startsWith("token=")) return p.substring(6); }
        return null;
    }
    static Map<String,String> getSession(HttpExchange ex) { String t=getToken(ex); return t!=null?sessions.get(t):null; }
    static boolean isAdmin(HttpExchange ex) { Map<String,String> s=getSession(ex); return s!=null&&"true".equals(s.get("isAdmin")); }

    // ── HANDLERS ────────────────────────────────────────────
    static void handleStatic(HttpExchange ex) throws IOException {
        byte[] body = frontendHtml.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");
        ex.sendResponseHeaders(200,body.length); ex.getResponseBody().write(body); ex.getResponseBody().close();
    }

    static void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send405(ex); return; }
        Map<String,String> req = parseJsonObject(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String crm=req.getOrDefault("crm","").trim(), senha=req.getOrDefault("senha","").trim();

        if (ADMIN_CRM.equals(crm) && ADMIN_SENHA.equals(senha)) {
            String token = UUID.randomUUID().toString();
            Map<String,String> sess = new LinkedHashMap<>();
            sess.put("nome",ADMIN_NOME); sess.put("crm",crm); sess.put("isAdmin","true"); sess.put("token",token);
            sessions.put(token, sess);
            long pend = countNotifPendentes();
            sendJson(ex,200,"{\"token\":\""+token+"\",\"nome\":\""+ADMIN_NOME+"\",\"crm\":\""+crm+"\",\"isAdmin\":true,\"notificacoesPendentes\":"+pend+"}");
            return;
        }

        String[] medico = medicos.stream().filter(m -> crm.equals(String.valueOf(m[0])) || crm.equals(m[4])).findFirst().orElse(null);
        if (medico != null) {
            String tel = medico[2].replaceAll("[^0-9]","");
            String senhaLimpa = senha.replaceAll("[^0-9]","");
            if (tel.endsWith(senhaLimpa) || senhaLimpa.equals(tel)) {
                String token = UUID.randomUUID().toString();
                Map<String,String> sess = new LinkedHashMap<>();
                sess.put("nome",medico[1]); sess.put("crm",String.valueOf(medico[0])); sess.put("isAdmin","false"); sess.put("token",token);
                sessions.put(token, sess);
                long pend = countNotifPendentes();
                sendJson(ex,200,"{\"token\":\""+token+"\",\"nome\":\""+escapeJson(medico[1])+"\",\"crm\":\""+medico[0]+"\",\"isAdmin\":false,\"notificacoesPendentes\":"+pend+"}");
                return;
            }
        }
        sendJson(ex,401,"{\"error\":\"CRM ou senha invalidos\"}");
    }

    static void handleLogout(HttpExchange ex) throws IOException {
        String token=getToken(ex); if(token!=null) sessions.remove(token);
        sendJson(ex,200,"{\"ok\":true}");
    }

    static void handleMe(HttpExchange ex) throws IOException {
        Map<String,String> sess=getSession(ex);
        if(sess==null){sendJson(ex,401,"{\"error\":\"Nao autenticado\"}");return;}
        long pend=countNotifPendentes();
        sendJson(ex,200,"{\"nome\":\""+escapeJson(sess.get("nome"))+"\",\"crm\":\""+sess.get("crm")+"\",\"isAdmin\":"+sess.get("isAdmin")+",\"notificacoesPendentes\":"+pend+",\"sseClients\":"+sseClients.size()+"}");
    }

    static void handleEscala(HttpExchange ex) throws IOException {
        String method=ex.getRequestMethod(), path=ex.getRequestURI().getPath();
        if("GET".equals(method)){
            Map<String,String> params=parseQuery(ex.getRequestURI().getQuery());
            String mes=params.get("mes"),medico=params.get("medico"),status=params.get("status");
            List<String[]> result=escala.stream()
                .filter(r->mes==null||r[1].startsWith(mes))
                .filter(r->medico==null||r[5].equals(medico))
                .filter(r->status==null||r[6].equals(status))
                .collect(Collectors.toList());
            sendJson(ex,200,arraysToJson(result));
        } else if("PUT".equals(method)){
            if(!isAdmin(ex)){sendJson(ex,403,"{\"error\":\"Apenas o administrador pode editar a escala\"}");return;}
            String[] parts=path.split("/"); String id=parts[parts.length-1];
            Map<String,String> req=parseJsonObject(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String[] plantao=escala.stream().filter(r->r[0].equals(id)).findFirst().orElse(null);
            if(plantao==null){sendJson(ex,404,"{\"error\":\"Plantao nao encontrado\"}");return;}
            String oldPlantonista=plantao[5];
            if(req.containsKey("plantonista")) plantao[5]=req.get("plantonista");
            if(req.containsKey("status")) plantao[6]=req.get("status");
            if(req.containsKey("turno")) plantao[3]=req.get("turno");
            try {
                PreparedStatement ps=db.prepareStatement("INSERT OR REPLACE INTO escala_overrides (plantao_id,plantonista,status,turno,updated_at) VALUES (?,?,?,?,?)");
                ps.setString(1,id);ps.setString(2,plantao[5]);ps.setString(3,plantao[6]);ps.setString(4,plantao[3]);ps.setString(5,now());
                ps.executeUpdate();ps.close();
            } catch(Exception e){System.err.println("Erro override: "+e.getMessage());}

            String msg="Plantao "+plantao[1]+" ("+plantao[2]+"): "+oldPlantonista+" -> "+plantao[5];
            addNotificacaoAndBroadcast("ESCALA","Escala atualizada pelo Administrador",msg);

            // Broadcast escala update to all clients
            broadcast("escala_update", arrayToJson(plantao));

            sendJson(ex,200,arrayToJson(plantao));
        } else {send405(ex);}
    }

    static void handleMedicos(HttpExchange ex) throws IOException {
        if(!"GET".equals(ex.getRequestMethod())){send405(ex);return;}
        sendJson(ex,200,arraysToJson(medicos));
    }

    static void handleTrocas(HttpExchange ex) throws IOException {
        String method=ex.getRequestMethod(), path=ex.getRequestURI().getPath();
        try {
            if("GET".equals(method)){
                String status=parseQuery(ex.getRequestURI().getQuery()).get("status");
                sendJson(ex,200,objectsToJson(getTrocas(status)));
            } else if("POST".equals(method)){
                Map<String,String> req=parseJsonObject(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String sol=req.get("solicitante"),pid=req.get("plantaoId"),sub=req.get("substituto"),mot=req.get("motivo"),obs=req.getOrDefault("obs","");
                if(sol==null||pid==null||sub==null||mot==null||sol.isEmpty()||pid.isEmpty()||sub.isEmpty()||mot.isEmpty()){sendJson(ex,400,"{\"error\":\"Campos obrigatorios faltando\"}");return;}
                if(sol.equals(sub)){sendJson(ex,400,"{\"error\":\"Solicitante e substituto nao podem ser o mesmo\"}");return;}
                String[] plantao=escala.stream().filter(r->r[0].equals(pid)).findFirst().orElse(null);
                if(plantao==null){sendJson(ex,404,"{\"error\":\"Plantao nao encontrado\"}");return;}
                String id="TRK-"+String.format("%04d",trocaCounter.getAndIncrement());
                PreparedStatement ps=db.prepareStatement("INSERT INTO trocas (id,plantao_id,data_plantao,dia_plantao,turno,plantonista_original,solicitante,substituto,motivo,obs,status,data_solicitacao,obs_gestor,data_decisao) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                ps.setString(1,id);ps.setString(2,pid);ps.setString(3,plantao[1]);ps.setString(4,plantao[2]);
                ps.setString(5,plantao[3]);ps.setString(6,plantao[5]);ps.setString(7,sol);ps.setString(8,sub);
                ps.setString(9,mot);ps.setString(10,obs);ps.setString(11,"PENDENTE");ps.setString(12,now());
                ps.setString(13,"");ps.setString(14,"");ps.executeUpdate();ps.close();

                addNotificacaoAndBroadcast("TROCA","Nova solicitacao de troca",sol+" solicita troca do plantao "+plantao[1]+" para "+sub);
                broadcast("troca_nova","{\"id\":\""+id+"\",\"solicitante\":\""+escapeJson(sol)+"\",\"substituto\":\""+escapeJson(sub)+"\",\"dataPlantao\":\""+plantao[1]+"\"}");

                Map<String,String> t=new LinkedHashMap<>();
                t.put("id",id);t.put("plantaoId",pid);t.put("dataPlantao",plantao[1]);t.put("diaPlantao",plantao[2]);
                t.put("turno",plantao[3]);t.put("solicitante",sol);t.put("substituto",sub);t.put("motivo",mot);
                t.put("obs",obs);t.put("status","PENDENTE");t.put("dataSolicitacao",now());t.put("obsGestor","");t.put("dataDecisao","");
                sendJson(ex,200,objectToJson(t));
            } else if("PUT".equals(method)){
                if(!isAdmin(ex)){sendJson(ex,403,"{\"error\":\"Apenas o administrador pode processar trocas\"}");return;}
                String[] parts=path.split("/"); String id=parts[parts.length-1];
                Map<String,String> req=parseJsonObject(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                String acao=req.get("acao"),obsGestor=req.getOrDefault("obsGestor","");
                ResultSet rs=db.prepareStatement("SELECT * FROM trocas WHERE id='"+id+"'").executeQuery();
                if(!rs.next()){sendJson(ex,404,"{\"error\":\"Troca nao encontrada\"}");rs.close();return;}
                if(!"PENDENTE".equals(rs.getString("status"))){sendJson(ex,400,"{\"error\":\"Troca ja processada\"}");rs.close();return;}
                String pid=rs.getString("plantao_id"),sub=rs.getString("substituto"),sol=rs.getString("solicitante"),dp=rs.getString("data_plantao");
                rs.close();
                String nowStr=now();
                if("aprovar".equals(acao)){
                    String og=obsGestor.isEmpty()?"Aprovado pelo Dr. Artur Cesar.":obsGestor;
                    PreparedStatement ps=db.prepareStatement("UPDATE trocas SET status='APROVADA',obs_gestor=?,data_decisao=? WHERE id=?");
                    ps.setString(1,og);ps.setString(2,nowStr);ps.setString(3,id);ps.executeUpdate();ps.close();
                    for(String[] p:escala){if(p[0].equals(pid)){p[5]=sub;break;}}
                    PreparedStatement ps2=db.prepareStatement("INSERT OR REPLACE INTO escala_overrides (plantao_id,plantonista,status,turno,updated_at) VALUES (?,?,?,?,?)");
                    String[] pArr=escala.stream().filter(r->r[0].equals(pid)).findFirst().orElse(new String[]{"","","","","","",""});
                    ps2.setString(1,pid);ps2.setString(2,sub);ps2.setString(3,pArr[6]);ps2.setString(4,pArr[3]);ps2.setString(5,nowStr);ps2.executeUpdate();ps2.close();
                    addNotificacaoAndBroadcast("TROCA_APROVADA","Troca APROVADA pelo Administrador","Troca "+id+": "+sol+" -> "+sub+" em "+dp+" foi APROVADA.");
                    broadcast("troca_aprovada","{\"id\":\""+id+"\",\"plantaoId\":\""+pid+"\",\"novoPlantonistaId\":\""+escapeJson(sub)+"\",\"dataPlantao\":\""+dp+"\"}");
                } else if("recusar".equals(acao)){
                    if(obsGestor.isEmpty()){sendJson(ex,400,"{\"error\":\"Motivo da recusa obrigatorio\"}");return;}
                    PreparedStatement ps=db.prepareStatement("UPDATE trocas SET status='RECUSADA',obs_gestor=?,data_decisao=? WHERE id=?");
                    ps.setString(1,obsGestor);ps.setString(2,nowStr);ps.setString(3,id);ps.executeUpdate();ps.close();
                    addNotificacaoAndBroadcast("TROCA_RECUSADA","Troca RECUSADA pelo Administrador","Troca "+id+" em "+dp+" foi RECUSADA. Motivo: "+obsGestor);
                    broadcast("troca_recusada","{\"id\":\""+id+"\",\"motivo\":\""+escapeJson(obsGestor)+"\"}");
                } else{sendJson(ex,400,"{\"error\":\"Acao invalida\"}");return;}
                List<Map<String,String>> updated=getTrocas(null).stream().filter(t->t.get("id").equals(id)).collect(Collectors.toList());
                sendJson(ex,200,updated.isEmpty()?"{}":objectToJson(updated.get(0)));
            } else{send405(ex);}
        } catch(Exception e){sendJson(ex,500,"{\"error\":\""+escapeJson(e.getMessage())+"\"}");e.printStackTrace();}
    }

    static void handleCheckin(HttpExchange ex) throws IOException {
        try {
            if("GET".equals(ex.getRequestMethod())){
                String medico=parseQuery(ex.getRequestURI().getQuery()).get("medico");
                String sql="SELECT * FROM checkins"+(medico!=null&&!medico.isEmpty()?" WHERE medico=?":"")+" ORDER BY data DESC LIMIT 100";
                PreparedStatement ps=db.prepareStatement(sql);
                if(medico!=null&&!medico.isEmpty()) ps.setString(1,medico);
                ResultSet rs=ps.executeQuery();
                List<Map<String,String>> result=new ArrayList<>();
                while(rs.next()){Map<String,String> c=new LinkedHashMap<>();c.put("id",rs.getString("id"));c.put("tipo",rs.getString("tipo"));c.put("medico",rs.getString("medico"));c.put("plantaoId",rs.getString("plantao_id"));c.put("lat",rs.getString("lat"));c.put("lon",rs.getString("lon"));c.put("distanciaKm",rs.getString("distancia_km"));c.put("data",rs.getString("data"));result.add(c);}
                rs.close();ps.close();
                sendJson(ex,200,objectsToJson(result));
                return;
            }
            if(!"POST".equals(ex.getRequestMethod())){send405(ex);return;}
            Map<String,String> req=parseJsonObject(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String latStr=req.get("lat"),lonStr=req.get("lon"),tipo=req.getOrDefault("tipo","checkin");
            String medico=req.getOrDefault("medico",""),plantaoId=req.getOrDefault("plantaoId","");
            if(latStr==null||lonStr==null){sendJson(ex,400,"{\"error\":\"Coordenadas GPS obrigatorias\"}");return;}
            double lat=Double.parseDouble(latStr),lon=Double.parseDouble(lonStr);
            double distKm=haversineKm(lat,lon,HOSP_LAT,HOSP_LON);
            if(distKm>CHECKIN_RADIUS_KM){sendJson(ex,400,"{\"error\":\"Voce esta a "+String.format("%.1f",distKm)+"km do hospital. O check-in requer estar dentro de "+CHECKIN_RADIUS_KM+"km.\",\"distancia\":"+String.format("%.2f",distKm)+"}");return;}
            String id=UUID.randomUUID().toString().substring(0,8).toUpperCase();
            PreparedStatement ps=db.prepareStatement("INSERT INTO checkins (id,tipo,medico,plantao_id,lat,lon,distancia_km,data) VALUES (?,?,?,?,?,?,?,?)");
            ps.setString(1,id);ps.setString(2,tipo);ps.setString(3,medico);ps.setString(4,plantaoId);
            ps.setString(5,latStr);ps.setString(6,lonStr);ps.setString(7,String.format("%.2f",distKm));ps.setString(8,now());
            ps.executeUpdate();ps.close();
            String tipoLabel="checkin".equals(tipo)?"CHECK-IN":"CHECK-OUT";
            addNotificacaoAndBroadcast("CHECKIN",tipoLabel+" registrado",medico+" realizou "+tipoLabel+" em "+now()+" ("+String.format("%.0f",distKm*1000)+"m do hospital)");
            broadcast("checkin","{\"medico\":\""+escapeJson(medico)+"\",\"tipo\":\""+tipo+"\",\"distanciaKm\":"+String.format("%.2f",distKm)+"}");
            sendJson(ex,200,"{\"ok\":true,\"tipo\":\""+tipo+"\",\"distanciaKm\":"+String.format("%.2f",distKm)+",\"dentroDoRaio\":true,\"data\":\""+now()+"\"}");
        } catch(Exception e){sendJson(ex,500,"{\"error\":\""+escapeJson(e.getMessage())+"\"}");e.printStackTrace();}
    }

    static void handleNotificacoes(HttpExchange ex) throws IOException {
        try {
            if("GET".equals(ex.getRequestMethod())){
                ResultSet rs=db.createStatement().executeQuery("SELECT * FROM notificacoes ORDER BY id DESC LIMIT 100");
                List<Map<String,String>> result=new ArrayList<>();
                while(rs.next()){Map<String,String> n=new LinkedHashMap<>();n.put("id",String.valueOf(rs.getInt("id")));n.put("tipo",rs.getString("tipo"));n.put("titulo",rs.getString("titulo"));n.put("mensagem",rs.getString("mensagem"));n.put("data",rs.getString("data"));n.put("lida",rs.getInt("lida")==1?"true":"false");result.add(n);}
                rs.close();
                sendJson(ex,200,objectsToJson(result));
            } else if("PUT".equals(ex.getRequestMethod())){
                db.createStatement().execute("UPDATE notificacoes SET lida=1");
                sendJson(ex,200,"{\"ok\":true}");
            } else{send405(ex);}
        } catch(Exception e){sendJson(ex,500,"{\"error\":\""+escapeJson(e.getMessage())+"\"}");e.printStackTrace();}
    }

    static void handleStats(HttpExchange ex) throws IOException {
        try {
            if(!"GET".equals(ex.getRequestMethod())){send405(ex);return;}
            String hoje=LocalDate.now().toString(),mes=hoje.substring(0,7);
            long agendados=escala.stream().filter(r->"AGENDADO".equals(r[6])).count();
            long aDefinir=escala.stream().filter(r->"A DEFINIR".equals(r[6])).count();
            long mesCount=escala.stream().filter(r->r[1].startsWith(mes)&&"AGENDADO".equals(r[6])).count();
            ResultSet rs1=db.createStatement().executeQuery("SELECT COUNT(*) FROM trocas WHERE status='PENDENTE'");long pendentes=rs1.next()?rs1.getLong(1):0;rs1.close();
            ResultSet rs2=db.createStatement().executeQuery("SELECT COUNT(*) FROM notificacoes WHERE lida=0");long notifPend=rs2.next()?rs2.getLong(1):0;rs2.close();
            ResultSet rs3=db.createStatement().executeQuery("SELECT COUNT(*) FROM checkins");long totalCheckins=rs3.next()?rs3.getLong(1):0;rs3.close();
            String[] hojeP=escala.stream().filter(r->r[1].equals(hoje)).findFirst().orElse(null);
            StringBuilder sb=new StringBuilder("{");
            sb.append("\"total\":").append(escala.size()).append(",");
            sb.append("\"agendados\":").append(agendados).append(",");
            sb.append("\"aDefinir\":").append(aDefinir).append(",");
            sb.append("\"hoje\":").append(hojeP!=null?arrayToJson(hojeP):"null").append(",");
            sb.append("\"mesCount\":").append(mesCount).append(",");
            sb.append("\"trocasPendentes\":").append(pendentes).append(",");
            sb.append("\"notificacoesPendentes\":").append(notifPend).append(",");
            sb.append("\"totalCheckins\":").append(totalCheckins).append(",");
            sb.append("\"medicos\":").append(medicos.size()).append(",");
            sb.append("\"sseClients\":").append(sseClients.size()).append(",");
            sb.append("\"hospitalLat\":").append(HOSP_LAT).append(",");
            sb.append("\"hospitalLon\":").append(HOSP_LON).append(",");
            sb.append("\"checkinRaioKm\":").append(CHECKIN_RADIUS_KM);
            sb.append("}");
            sendJson(ex,200,sb.toString());
        } catch(Exception e){sendJson(ex,500,"{\"error\":\""+escapeJson(e.getMessage())+"\"}");e.printStackTrace();}
    }

    // ── HELPERS ──────────────────────────────────────────────
    static long countNotifPendentes() {
        try { ResultSet rs=db.createStatement().executeQuery("SELECT COUNT(*) FROM notificacoes WHERE lida=0"); long c=rs.next()?rs.getLong(1):0; rs.close(); return c; } catch(Exception e){return 0;}
    }

    static List<Map<String,String>> getTrocas(String statusFilter) throws Exception {
        String sql="SELECT * FROM trocas"+(statusFilter!=null&&!statusFilter.isEmpty()?" WHERE status=?":"")+" ORDER BY data_solicitacao DESC";
        PreparedStatement ps=db.prepareStatement(sql);
        if(statusFilter!=null&&!statusFilter.isEmpty()) ps.setString(1,statusFilter);
        ResultSet rs=ps.executeQuery();
        List<Map<String,String>> result=new ArrayList<>();
        while(rs.next()){Map<String,String> t=new LinkedHashMap<>();t.put("id",rs.getString("id"));t.put("plantaoId",rs.getString("plantao_id"));t.put("dataPlantao",rs.getString("data_plantao"));t.put("diaPlantao",rs.getString("dia_plantao"));t.put("turno",rs.getString("turno"));t.put("plantonistaOriginal",rs.getString("plantonista_original"));t.put("solicitante",rs.getString("solicitante"));t.put("substituto",rs.getString("substituto"));t.put("motivo",rs.getString("motivo"));t.put("obs",rs.getString("obs"));t.put("status",rs.getString("status"));t.put("dataSolicitacao",rs.getString("data_solicitacao"));t.put("obsGestor",rs.getString("obs_gestor"));t.put("dataDecisao",rs.getString("data_decisao"));result.add(t);}
        rs.close();ps.close();
        return result;
    }

    static double haversineKm(double lat1,double lon1,double lat2,double lon2){double R=6371.0,dLat=Math.toRadians(lat2-lat1),dLon=Math.toRadians(lon2-lon1);double a=Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));}

    static void sendJson(HttpExchange ex,int code,String json) throws IOException {
        byte[] body=json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin","*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers","Authorization, Content-Type");
        ex.sendResponseHeaders(code,body.length);ex.getResponseBody().write(body);ex.getResponseBody().close();
    }
    static void send405(HttpExchange ex) throws IOException{ex.sendResponseHeaders(405,-1);ex.getResponseBody().close();}
    static Map<String,String> parseQuery(String query){Map<String,String> map=new HashMap<>();if(query==null)return map;for(String pair:query.split("&")){String[] kv=pair.split("=",2);if(kv.length==2)map.put(kv[0],URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return map;}
    static String now(){return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));}
    static String escapeJson(String s){if(s==null)return "";return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
    static String arrayToJson(String[] arr){StringBuilder sb=new StringBuilder("[");for(int i=0;i<arr.length;i++){if(i>0)sb.append(",");sb.append("\"").append(escapeJson(arr[i])).append("\"");}return sb.append("]").toString();}
    static String arraysToJson(List<String[]> list){return "["+list.stream().map(EscalaServer::arrayToJson).collect(Collectors.joining(","))+"]";}
    static String objectToJson(Map<String,String> map){StringBuilder sb=new StringBuilder("{");boolean first=true;for(Map.Entry<String,String> e:map.entrySet()){if(!first)sb.append(",");sb.append("\"").append(escapeJson(e.getKey())).append("\":\"").append(escapeJson(e.getValue())).append("\"");first=false;}return sb.append("}").toString();}
    static String objectsToJson(List<Map<String,String>> list){return "["+list.stream().map(EscalaServer::objectToJson).collect(Collectors.joining(","))+"]";}

    static List<String[]> parseJsonArrayOfArrays(String json){List<String[]> result=new ArrayList<>();json=json.trim();if(!json.startsWith("["))return result;int i=1;while(i<json.length()){int start=json.indexOf('[',i);if(start<0)break;int depth=0,end=-1;for(int j=start;j<json.length();j++){char c=json.charAt(j);if(c=='[')depth++;else if(c==']'){depth--;if(depth==0){end=j;break;}}}if(end>start){String[] arr=parseStringArray(json.substring(start+1,end));if(arr.length>0)result.add(arr);i=end+1;}else break;}return result;}
    static String[] parseStringArray(String inner){List<String> vals=new ArrayList<>();int i=0;while(i<inner.length()){if(inner.charAt(i)=='"'){StringBuilder sb=new StringBuilder();i++;while(i<inner.length()){char c=inner.charAt(i);if(c=='\\'&&i+1<inner.length()){char n=inner.charAt(i+1);if(n=='"')sb.append('"');else if(n=='\\')sb.append('\\');else if(n=='n')sb.append('\n');else sb.append(n);i+=2;}else if(c=='"'){i++;break;}else{sb.append(c);i++;}}vals.add(sb.toString());}else if((inner.charAt(i)>='0'&&inner.charAt(i)<='9')||inner.charAt(i)=='-'){int start=i;while(i<inner.length()&&inner.charAt(i)!=','&&inner.charAt(i)!=']')i++;vals.add(inner.substring(start,i).trim());}else{i++;}}return vals.toArray(new String[0]);}
    static Map<String,String> parseJsonObject(String json){Map<String,String> map=new LinkedHashMap<>();json=json.trim();if(!json.startsWith("{"))return map;json=json.substring(1,json.lastIndexOf('}'));int i=0;while(i<json.length()){int ks=json.indexOf('"',i);if(ks<0)break;int ke=json.indexOf('"',ks+1);if(ke<0)break;String key=json.substring(ks+1,ke);int colon=json.indexOf(':',ke);if(colon<0)break;int vs=colon+1;while(vs<json.length()&&json.charAt(vs)==' ')vs++;String value;int nextI;if(vs<json.length()&&json.charAt(vs)=='"'){StringBuilder sb=new StringBuilder();int j=vs+1;while(j<json.length()){char c=json.charAt(j);if(c=='\\'&&j+1<json.length()){char n=json.charAt(j+1);if(n=='"')sb.append('"');else if(n=='\\')sb.append('\\');else if(n=='n')sb.append('\n');else sb.append(n);j+=2;}else if(c=='"'){j++;break;}else{sb.append(c);j++;}}value=sb.toString();nextI=j;}else{int ve=vs;while(ve<json.length()&&json.charAt(ve)!=','&&json.charAt(ve)!='}')ve++;value=json.substring(vs,ve).trim();nextI=ve;}map.put(key,value);i=nextI;}return map;}
}