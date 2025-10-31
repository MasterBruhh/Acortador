package edu.pucmm.eict.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import edu.pucmm.eict.modelos.AccessDetail;
import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.inc;
import static com.mongodb.client.model.Updates.push;

public class UrlService {
    private MongoCollection<Document> urlCollection;
    // TTL en segundos para enlaces anónimos (por ejemplo, 1 hora)
    private static final int ANONYMOUS_TTL_SECONDS = 3600;

    public UrlService() {
        String mongoUrl = System.getenv("MONGODB_URL");
        if(mongoUrl == null || mongoUrl.isEmpty()){
            throw new RuntimeException("La variable de ambiente MONGODB_URL no está configurada.");
        }
        MongoClient mongoClient = MongoClients.create(mongoUrl);
        MongoDatabase database = mongoClient.getDatabase("acortador");
        urlCollection = database.getCollection("urls");

        // Crear un índice TTL en el campo "expiresAt" para enlaces anónimos.
        IndexOptions indexOptions = new IndexOptions().expireAfter(0L, TimeUnit.SECONDS);
        urlCollection.createIndex(new Document("expiresAt", 1), indexOptions);

        System.out.println("Conectado a MongoDB en UrlService. Base de datos: " + database.getName());
    }

    private String generateShortUrl() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // Metodo que recibe el usuario que crea la URL.
    // Si el usuario es anónimo, se añade el campo "expiresAt" para que MongoDB lo elimine automáticamente.
    public Url saveUrl(String originalUrl, Usuario user) {
        String shortCode = generateShortUrl();
        Url url = new Url(originalUrl, shortCode);
        url.setUser(user);

        // Preparar subdocumento para el usuario (datos básicos)
        Document userDoc = null;
        if (user != null) {
            userDoc = new Document("username", user.getUsername())
                    .append("role", user.getRole());
        }

        Document doc = new Document("_id", new ObjectId())
                .append("originalUrl", originalUrl)
                .append("shortUrl", shortCode)
                .append("accessCount", 0)
                .append("accessTimes", new ArrayList<Date>())
                .append("accessDetails", new ArrayList<Document>())
                .append("user", userDoc);

        // Si el usuario es anónimo, agregar el campo "expiresAt"
       // if (user != null && "anonymous".equals(user.getRole())) {
            // Date expiresAt = new Date(System.currentTimeMillis() + ANONYMOUS_TTL_SECONDS * 1000L);
          //  doc.append("expiresAt", expiresAt);
        //}

        urlCollection.insertOne(doc);
        return url;
    }

    public String getOriginalUrl(String shortUrl) {
        Document doc = urlCollection.find(new Document("shortUrl", shortUrl)).first();
        if (doc != null) {
            urlCollection.updateOne(new Document("shortUrl", shortUrl),
                    combine(inc("accessCount", 1), push("accessTimes", new Date())));
            return doc.getString("originalUrl");
        }
        return null;
    }

    public Url getUrl(String shortUrl) {
        Document doc = urlCollection.find(new Document("shortUrl", shortUrl)).first();
        if (doc != null) {
            return docToUrl(doc);
        }
        return null;
    }

    public Collection<Url> getAllUrls() {
        List<Url> list = new ArrayList<>();
        for (Document doc : urlCollection.find()) {
            list.add(docToUrl(doc));
        }
        return list;
    }

    public void recordAccess(Url url, AccessDetail detail) {
        Document detailDoc = new Document("timestamp", detail.getTimestamp())
                .append("browser", detail.getBrowser())
                .append("ip", detail.getIp())
                .append("clientDomain", detail.getClientDomain())
                .append("platform", detail.getPlatform());
        urlCollection.updateOne(new Document("shortUrl", url.getShortUrl()),
                combine(inc("accessCount", 1),
                        push("accessDetails", detailDoc),
                        push("accessTimes", detail.getTimestamp())));
    }

    public boolean deleteUrl(String shortUrl) {
        return urlCollection.deleteOne(new Document("shortUrl", shortUrl)).getDeletedCount() > 0;
    }

    // Convierte el documento de MongoDB a un objeto Url, reconstruyendo también el objeto Usuario.
    private Url docToUrl(Document doc) {
        Url url = new Url(doc.getString("originalUrl"), doc.getString("shortUrl"));
        url.setAccessCount(doc.getInteger("accessCount", 0));
        url.setId(doc.getObjectId("_id"));

        // Recuperar accessTimes (lista de Date)
        List<Date> accessTimes = doc.getList("accessTimes", Date.class);
        if (accessTimes != null) {
            url.getAccessTimes().addAll(accessTimes);
        }

        // Recuperar accessDetails (lista de Document) y convertir cada uno a AccessDetail
        List<Document> detailsDocs = doc.getList("accessDetails", Document.class);
        if (detailsDocs != null) {
            for (Document d : detailsDocs) {
                AccessDetail detail = new AccessDetail(
                        d.getDate("timestamp"),
                        d.getString("browser"),
                        d.getString("ip"),
                        d.getString("clientDomain"),
                        d.getString("platform")
                );
                url.getAccessDetails().add(detail);
            }
        }

        // Recuperar el usuario si existe
        Document userDoc = (Document) doc.get("user");
        if (userDoc != null) {
            Usuario user = new Usuario();
            user.setUsername(userDoc.getString("username"));
            user.setRole(userDoc.getString("role"));
            url.setUser(user);
        }

        return url;
    }


    public boolean updateShortUrl(String originalShort, String newShort) {
        Document filter = new Document("shortUrl", originalShort);
        Document update = new Document("$set", new Document("shortUrl", newShort));
        return urlCollection.updateOne(filter, update).getModifiedCount() > 0;
    }

}
