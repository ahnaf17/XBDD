/**
 * Copyright (C) 2015 Orion Health (Orchestral Development Ltd)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xbdd.webapp.resource.feature;

import com.mongodb.*;
import xbdd.webapp.factory.MongoDBAccessor;
import xbdd.webapp.util.Coordinates;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

@Path("/favourites")
public class Favourites {

	private final MongoDBAccessor client;

	@Inject
	public Favourites(final MongoDBAccessor client) {
		this.client = client;
	}

	public void setFavouriteStateOfProduct(final String product,
			final boolean state,
			final HttpServletRequest req) {

		final DB db = this.client.getDB("bdd");
		final DBCollection collection = db.getCollection("users");

		final BasicDBObject user = new BasicDBObject();
		user.put("user_id", req.getRemoteUser());

		final DBObject blank = new BasicDBObject();
		collection.findAndModify(user, blank, blank, false, new BasicDBObject("$set", user), true, true);

		//User exists
		final DBObject favourites = new BasicDBObject("favourites." + product, state);
		final DBObject update = new BasicDBObject("$set", favourites);
		collection.update(user, update);
	}

	@PUT
	@Path("/{product}")
	@Produces("application/json")
	public Response productFavouriteStateOn(@PathParam("product") final String product,
			@Context final HttpServletRequest req) {

		setFavouriteStateOfProduct(product, true, req);
		return Response.ok().build();
	}

	@DELETE
	@Path("/{product}")
	@Produces("application/json")
	public Response productFavouriteStateOff(@PathParam("product") final String product,
			@Context final HttpServletRequest req) {

		setFavouriteStateOfProduct(product, false, req);
		return Response.ok().build();
	}

	@GET
	@Path("/{product}")
	@Produces("application/json")
	public boolean getFavouriteStateOfProduct(@PathParam("product") final String product,
			@Context final HttpServletRequest req) {
		final DB db = this.client.getDB("bdd");
		final DBCollection collection = db.getCollection("users");

		final BasicDBObject query = new BasicDBObject("user_id", req.getRemoteUser());
		query.put("favourites." + product, true);

		final DBObject favState = collection.findOne(query);

		return (favState != null);
	}

	@GET
	@Path("/")
	@Produces("application/json")
	public DBObject getSummaryOfAllReports(@Context final HttpServletRequest req) {
		final DB db = this.client.getDB("bdd");
		final DBCollection collection = db.getCollection("summary");
		final DBCollection usersCollection = db.getCollection("users");

		final BasicDBObject user = new BasicDBObject();
		user.put("user_id", req.getRemoteUser());

		final DBObject blank = new BasicDBObject();
		DBObject uDoc = usersCollection.findAndModify(user, blank, blank, false,
				new BasicDBObject("$setOnInsert", new BasicDBObject("favourites", new BasicDBObject())), true, true);
		DBObject userFavourites;

		if (uDoc.containsField("favourites")) {
			userFavourites = (DBObject) uDoc.get("favourites");
		} else {
			userFavourites = new BasicDBObject();
		}

		final DBCursor cursor = collection.find();

		try {
			final BasicDBList returns = new BasicDBList();
			DBObject doc;

			while (cursor.hasNext()) {
				doc = cursor.next();
				String product = ((String) ((DBObject) doc.get("coordinates")).get("product"));
				if (userFavourites.containsField(product) && (boolean) userFavourites.get(product)) {
					doc.put("favourite", userFavourites.get(product));
					returns.add(doc);
				}
			}

			return returns;
		} finally {
			cursor.close();
		}
	}

	private void setPinStateOfBuild(final String product,
			final String version,
			final String build,
			final boolean state) {

		final DB db = this.client.getDB("bdd");
		final DBCollection collection = db.getCollection("summary");

		final BasicDBObject query = new BasicDBObject("_id", product + "/" + version);
		final BasicDBObject toBePinned = new BasicDBObject("pinned", build);
		final String method;

		if (state) {
			method = "$addToSet";
		} else {
			method = "$pull";
		}

		collection.update(query, new BasicDBObject(method, toBePinned));
	}

	@PUT
	@Path("/pin/{product}/{major}.{minor}.{servicePack}/{build}/")
	@Produces("application/json")
	public Response pinABuild(@BeanParam Coordinates coordinates) {

		setPinStateOfBuild(coordinates.getProduct(), coordinates.getVersionString(), coordinates.getBuild(), true);
		return Response.ok().build();
	}

	@DELETE
	@Path("/pin/{product}/{major}.{minor}.{servicePack}/{build}/")
	@Produces("application/json")
	public Response unPinABuild(@BeanParam Coordinates coordinates) {

		setPinStateOfBuild(coordinates.getProduct(), coordinates.getVersionString(), coordinates.getBuild(), false);
		return Response.ok().build();
	}
}