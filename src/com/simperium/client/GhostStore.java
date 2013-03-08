package com.simperium.client;

import android.content.Context;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;

import java.util.Map;

public class GhostStore {
	
	private static final String DATABASE_NAME="simperium-ghost";
	private static final String GHOSTS_TABLE_NAME="ghosts";
	private static final String VERSIONS_TABLE_NAME="changeVersions";
	private static final String CREATE_TABLE_GHOSTS="CREATE TABLE IF NOT EXISTS ghosts (id INTEGER PRIMARY KEY AUTOINCREMENT, bucketName VARCHAR(63), simperiumKey VARCHAR(255), version INTEGER, payload TEXT, UNIQUE(bucketName, simperiumKey) ON CONFLICT REPLACE)";
	private static final String CREATE_TABLE_CHANGE_VERSIONS="CREATE TABLE IF NOT EXISTS changeVersions (id INTEGER PRIMARY KEY AUTOINCREMENT, bucketName VARCHAR(63), changeVersion VARCHAR(255), UNIQUE(bucketName))";
	private static final Integer VERSION=1;
	private static final String BUCKET_NAME_FIELD="bucketName";
	private static final String VERSION_FIELD="version";
	private static final String OBJECT_KEY_FIELD="simperiumKey";
	private static final String PAYLOAD_FIELD="payload";
	private static final String CHANGE_VERSION_FIELD="changeVersion";
	
	private SQLiteDatabase database;

	public GhostStore(Context context) {
		database = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
		database.execSQL(CREATE_TABLE_GHOSTS);
		database.execSQL(CREATE_TABLE_CHANGE_VERSIONS);
		database.setVersion(VERSION);
	}
	
	protected void reset(){
		database.delete(GHOSTS_TABLE_NAME, null, null);
		database.delete(VERSIONS_TABLE_NAME, null, null);
	}
	
	protected Cursor queryChangeVersion(Bucket bucket){
		String[] columns = { BUCKET_NAME_FIELD, CHANGE_VERSION_FIELD };
		String[] args = { bucket.getName() };
		Cursor cursor = database.query(VERSIONS_TABLE_NAME, columns, "bucketName=?", args, null, null, null);
		int count = cursor.getCount();
		return cursor;
	}
	
	protected boolean hasChangeVersion(Bucket bucket){
		Cursor cursor = queryChangeVersion(bucket);
		int count = cursor.getCount();
		cursor.close();
		return count > 0;
	}
	
	protected boolean hasChangeVersion(Bucket bucket, String cv){
		// Simperium.log(String.format("Do we have CV: %s", cv));
		String storedVersion = getChangeVersion(bucket);
		return storedVersion != null && storedVersion.equals(cv);
	}
	
	protected String getChangeVersion(Bucket bucket){
		Cursor cursor = queryChangeVersion(bucket);
		String storedVersion = null;
		Simperium.log(String.format("Column count %d Result count %d", cursor.getColumnCount(), cursor.getCount()));
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			storedVersion = cursor.getString(1);
		}
		cursor.close();
		return storedVersion;
	}
	
	protected void setChangeVersion(Bucket bucket, String cv){
		ContentValues values = new ContentValues();
		String where = "bucketName=?";
		String[] args = { bucket.getName() };
		values.put(CHANGE_VERSION_FIELD, cv);
		values.put(BUCKET_NAME_FIELD, bucket.getName());
		if (hasChangeVersion(bucket)) {
			database.update(VERSIONS_TABLE_NAME, values, where, args);
		} else {
			database.insert(VERSIONS_TABLE_NAME, null, values);
		}
		Simperium.log(String.format("Set change version to: %s", cv));
	}

	protected void saveGhost(Bucket bucket, Bucket.Ghost ghost){
		// CREATE/UPDATE
		String where = "bucketName=? AND simperiumKey=?";
		String[] args = { bucket.getName(), ghost.getSimperiumKey() };
		String[] columns = { BUCKET_NAME_FIELD, VERSION_FIELD, OBJECT_KEY_FIELD };
		Cursor cursor = database.query(GHOSTS_TABLE_NAME, columns, where, args, null, null, null);
		ContentValues values = new ContentValues();
		values.put(BUCKET_NAME_FIELD, bucket.getName());
		values.put(VERSION_FIELD, ghost.getVersion());
		values.put(OBJECT_KEY_FIELD, ghost.getSimperiumKey());
		values.put(PAYLOAD_FIELD, serializeGhostData(ghost));
		if (cursor.getCount() > 0) {
			int count = database.update(GHOSTS_TABLE_NAME, values, where, args);
			Simperium.log(String.format("Updated ghost(%d): %s.%d", count, ghost.getSimperiumKey(), ghost.getVersion()));
		} else {
			long id = database.insertOrThrow(GHOSTS_TABLE_NAME, null, values);
			Simperium.log(String.format("Created ghost(id:%d): %s.%d", id, ghost.getSimperiumKey(), ghost.getVersion()));
		}
		cursor.close();
	}

	public Bucket.Ghost getGhost(Bucket bucket, String key){
		// public Cursor query (String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy)
		String[] columns = { BUCKET_NAME_FIELD, OBJECT_KEY_FIELD, VERSION_FIELD, PAYLOAD_FIELD };
		String where = "bucketName=? AND simperiumKey=?";
		String[] args = { bucket.getName(), key };
		Cursor cursor = database.query(GHOSTS_TABLE_NAME, columns, where, args, null, null, null);
		Bucket.Ghost ghost = null;
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			ghost = new Bucket.Ghost(cursor.getString(0), cursor.getInt(1), deserializeGhostData(cursor.getString(2)));
		}
		cursor.close();
		return ghost;
	}
	
	protected void deleteGhost(Bucket bucket, Bucket.Ghost ghost){
		// REMOVE
		
	}
	
	private String serializeGhostData(Bucket.Ghost ghost){
		return "";
	}
	
	private Map<String,Object> deserializeGhostData(String data){
		Map<String,Object> properties = null;
		try {
			properties = Channel.convertJSON(new org.json.JSONObject(data));
		} catch (org.json.JSONException e) {
			Simperium.log(String.format("Failed to deserialize ghost data %s", data), e);
		}
		return properties;
	}
	
}