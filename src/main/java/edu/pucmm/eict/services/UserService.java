package edu.pucmm.eict.services;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import edu.pucmm.eict.modelos.Usuario;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UserService {
    private MongoCollection<Document> userCollection;

    public UserService() {
        String mongoUrl = System.getenv("MONGODB_URL");
        if(mongoUrl == null || mongoUrl.isEmpty()){
            throw new RuntimeException("La variable de ambiente MONGODB_URL no está configurada.");
        }
        MongoClient mongoClient = MongoClients.create(mongoUrl);
        MongoDatabase database = mongoClient.getDatabase("acortador");
        userCollection = database.getCollection("users");
        System.out.println("Conectado a MongoDB en UserService. Base de datos: " + database.getName());
    }

    public Usuario getUserByUsername(String username) {
        Document doc = userCollection.find(new Document("username", username)).first();
        return (doc != null) ? docToUsuario(doc) : null;
    }

    public boolean register(String username, String password) {
        Document existing = userCollection.find(new Document("username", username)).first();
        if (existing != null) {
            return false;
        }
        // Creamos el documento con ObjectId auto generado
        Document userDoc = new Document("_id", new ObjectId())
                .append("username", username)
                .append("password", password)
                .append("role", "user");
        userCollection.insertOne(userDoc);
        return true;
    }

    public boolean registerAdmin(String username, String password) {
        Document existing = userCollection.find(new Document("username", username)).first();
        if (existing != null) {
            return false;
        }
        Document userDoc = new Document("_id", new ObjectId())
                .append("username", username)
                .append("password", password)
                .append("role", "admin");
        userCollection.insertOne(userDoc);
        return true;
    }

    public boolean authenticate(String username, String password) {
        Document doc = userCollection.find(new Document("username", username)).first();
        return (doc != null && doc.getString("password").equals(password));
    }

    public Collection<Usuario> getAllUsers() {
        List<Usuario> users = new ArrayList<>();
        for (Document doc : userCollection.find()) {
            Usuario user = docToUsuario(doc);
            users.add(user);
        }
        return users;
    }

    public boolean updateRole(String username, String newRole) {
        Document filter = new Document("username", username);
        Document update = new Document("$set", new Document("role", newRole));
        return userCollection.updateOne(filter, update).getModifiedCount() > 0;
    }

    public boolean deleteUser(String username) {
        // No permitir eliminar si el usuario es admin
        Document userDoc = userCollection.find(new Document("username", username)).first();
        if (userDoc != null && !"admin".equals(userDoc.getString("role"))) {
            return userCollection.deleteOne(new Document("username", username)).getDeletedCount() > 0;
        }
        return false;
    }

    // Inserta admin por defecto si no existe
    public void createDefaultAdmin() {
        Document exists = userCollection.find(new Document("username", "admin")).first();
        if (exists == null) {
            registerAdmin("admin", "admin");
            System.out.println("Default admin created.");
        } else {
            System.out.println("Admin user already exists.");
        }
    }

    private Usuario docToUsuario(Document doc) {
        // Convertir Document a modelo Usuario con ObjectId
        Usuario u = new Usuario(doc.getString("username"), doc.getString("password"), doc.getString("role"));
        u.setId(doc.getObjectId("_id"));
        return u;
    }

    public boolean updateUser(String username, String password, String role) {
        Document filter = new Document("username", username);
        Document updateFields = new Document("password", password)
                .append("role", role);
        Document update = new Document("$set", updateFields);
        return userCollection.updateOne(filter, update).getModifiedCount() > 0;
    }

}
