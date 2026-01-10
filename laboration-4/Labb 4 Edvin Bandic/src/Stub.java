import static com.mongodb.client.model.Filters.eq;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;

public class Stub {

    private static final Logger logger = LoggerFactory.getLogger(Stub.class);

    private static MongoCollection<Document> myCollection;

    public static void main(String[] args) throws Exception {
        initMongo();
        startServer();
    }

    private static void initMongo() throws Exception {
        Properties prop = new Properties();

        try (InputStream input = new FileInputStream("connection.properties")) {
            prop.load(input);
        }

        String connString = prop.getProperty("db.connection_string");
        String dbName = prop.getProperty("db.name");

        ConnectionString connectionString = new ConnectionString(connString);

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .build();

        MongoClient mongoClient = MongoClients.create(settings);

        MongoDatabase database = mongoClient.getDatabase(dbName);

        myCollection = database.getCollection("movies");

        logger.info("Connected to MongoDB, using database '{}'", dbName);
    }

    private static void startServer() {
        Javalin app = Javalin.create().start(4567);

        app.get("/title/{title}", Stub::getTitleByTitle);
        app.get("/fullplot/{title}" , Stub::getFullplotByTitle);
        app.get("/cast/{title}" , Stub::getCastByTitle);
        app.get("/genre/{genre}" , Stub::getGenreByGenre);
        app.get("/actor/{actor}" , Stub::getActorByActor);
        app.get("/similar/{title}", Stub::getSimilarMoviesByTitle);
        
        app.post("/title", Stub::createMovie);
        
        app.delete("/title/{title}", Stub::deleteMovie);
       
    }

    private static void getTitleByTitle(Context ctx) {
        String inputName = ctx.pathParam("title");
        String title = capitalizeFully(inputName.toLowerCase(Locale.ROOT));

        Document doc = myCollection.find(eq("title", title)).first();

        if (doc != null) {
            doc.remove("_id");
            doc.remove("poster");
            doc.remove("cast");
            doc.remove("fullplot");
            JsonObject response = JsonParser.parseString(doc.toJson()).getAsJsonObject();
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(response.toString());
        } else {
            ctx.status(404);
            ctx.contentType("application/json");
            ctx.result(jsonError("Title not found.").toString());
        }
    }
    
    private static void getFullplotByTitle(Context ctx) {
        String inputName = ctx.pathParam("title");
        String title = capitalizeFully(inputName.toLowerCase(Locale.ROOT));

        Document doc = myCollection.find(eq("title", title)).projection(new Document("title", 1).append("fullplot", 1).append("_id", 0)).first();

        if (doc != null) {
            JsonObject response = JsonParser.parseString(doc.toJson()).getAsJsonObject();
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(response.toString());
        } else {
            ctx.status(404);
            ctx.contentType("application/json");
            ctx.result(jsonError("Title not found.").toString());
        }
    }
    
    private static void getCastByTitle(Context ctx) {
        String inputName = ctx.pathParam("title");
        String title = capitalizeFully(inputName.toLowerCase(Locale.ROOT));

        Document doc = myCollection.find(eq("title", title)).projection(new Document("title", 1).append("cast", 1).append("_id", 0)).first();

        if (doc != null) {
            JsonObject response = JsonParser.parseString(doc.toJson()).getAsJsonObject();
            ctx.status(200);
            ctx.contentType("application/json");
            ctx.result(response.toString());
        } else {
            ctx.status(404);
            ctx.contentType("application/json");
            ctx.result(jsonError("Title not found.").toString());
        }
    }
    
    
    private static void getGenreByGenre(Context ctx) {
        String inputName = ctx.pathParam("genre");
        String genre = capitalizeFully(inputName.toLowerCase(Locale.ROOT));
        
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);


		FindIterable<Document> results = myCollection.find(eq("genres", genre))
				.limit(limit);
		

		JsonArray array = new JsonArray();
		
		for (Document doc : results) {
			 doc.remove("_id");
             doc.remove("poster");
             doc.remove("cast");
             doc.remove("fullplot");
             
			array.add(JsonParser.parseString(doc.toJson()).getAsJsonObject());
		}
		
		if(array.isEmpty()){
			ctx.status(404);
	        ctx.contentType("application/json");
	        ctx.result(jsonError("Genre not found.").toString());
	        return;
		}
		
		ctx.status(200);
        ctx.contentType("application/json");
        ctx.result(array.toString());

        }
    
    
    private static void getActorByActor(Context ctx) {
        String inputName = ctx.pathParam("actor");
        String actor = capitalizeFully(inputName.toLowerCase(Locale.ROOT));
        
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);

		FindIterable<Document> results = myCollection.find(eq("cast", actor))
				.limit(limit);
		

		JsonArray array = new JsonArray();
		
		for (Document doc : results) {
			 doc.remove("_id");
             
			array.add(JsonParser.parseString(doc.toJson()).getAsJsonObject());
		}
		
		if(array.isEmpty()){
			ctx.status(404);
	        ctx.contentType("application/json");
	        ctx.result(jsonError("Actor not found.").toString());
	        return;
		}
		
		ctx.status(200);
        ctx.contentType("application/json");
        ctx.result(array.toString());

        }
    
    
	private static void createMovie(Context ctx) {
		try {
			String requestBody = ctx.body();
			Document newDoc = Document.parse(requestBody);

			myCollection.insertOne(newDoc);

			ctx.status(202).contentType("application/json").result();

		} catch (Exception e) {
			logger.error("Insert failed: {}", e.getMessage());
			ctx.status(500).contentType("application/json")
					.result(jsonError("Failed to insert movie.").toString());
		}
	}
	
	private static void deleteMovie(Context ctx) {
		try {
			String inputName = ctx.pathParam("title");
	        String title = capitalizeFully(inputName.toLowerCase(Locale.ROOT));

			myCollection.deleteOne(eq("title", title));

			ctx.status(204).contentType("application/json").result();

		} catch (Exception e) {
			logger.error("Insert failed: {}", e.getMessage());
			ctx.status(409).contentType("application/json")
					.result(jsonError("Failed to delete movie.").toString());
		}
	}
    
    
	private static void getSimilarMoviesByTitle(Context ctx) {
		String inputCuisine = ctx.pathParam("title");
		String title = capitalizeFully(inputCuisine.toLowerCase(Locale.ROOT));

		int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
		
		Document movie = myCollection.find(eq("title", title)).first();
		
		
		if (movie == null) {
	        ctx.status(404).json(jsonError("Title not found"));
	        return;
	    }
		
		
		List<String> genres = movie.getList("genres", String.class);
		
		if (genres == null || genres.isEmpty()) {
	        ctx.status(404).json(jsonError("No genres found"));
	        return;
	    }
		
		String genre = genres.get(0);
		
		
		FindIterable<Document> results = myCollection.find(eq("genres", genre))
				.limit(limit);

		JsonArray array = new JsonArray();

		for (Document doc : results) {
			doc.remove("_id");
			doc.remove("poster");
	        doc.remove("cast");
	        doc.remove("fullplot");
			array.add(JsonParser.parseString(doc.toJson()).getAsJsonObject());
		}

		JsonObject response = new JsonObject();
		response.add("movies", array);
		
		if(array.isEmpty()){
			ctx.status(404);
	        ctx.contentType("application/json");
	        ctx.result(jsonError("Similar Movies not found.").toString());
	        return;
		}

		ctx.status(200).contentType("application/json").result(response.toString());
	}
    
    
    
    

  
    private static String capitalizeFully(String input) {
        String[] words = input.trim().split("\\s+");

        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                char first = Character.toUpperCase(word.charAt(0));
                String rest = word.substring(1).toLowerCase();

                result.append(first);
                result.append(rest);
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

 

    private static JsonObject jsonError(String error) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", error);
        return obj;
    }
}