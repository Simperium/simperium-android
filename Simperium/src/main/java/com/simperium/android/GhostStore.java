package com.simperium.android;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.simperium.client.Bucket;
import com.simperium.client.Ghost;
import com.simperium.client.GhostMissingException;
import com.simperium.client.GhostStorageProvider;
import com.simperium.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

public class GhostStore implements GhostStorageProvider {

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

    public GhostStore(SQLiteDatabase database) {
        this.database = database;
        prepare();
    }

    private void prepare() {
        database.execSQL(CREATE_TABLE_GHOSTS);
        database.execSQL(CREATE_TABLE_CHANGE_VERSIONS);
        database.execSQL(String.format("CREATE UNIQUE INDEX IF NOT EXISTS ghost_version ON ghosts (bucketName, simperiumKey, version)"));
        database.setVersion(VERSION);
    }

    protected void reset() {
        database.delete(GHOSTS_TABLE_NAME, null, null);
        database.delete(VERSIONS_TABLE_NAME, null, null);
    }

    @Override
    public void resetBucket(Bucket bucket) {
        String[] args = { bucket.getName() };
        String where = "bucketName=?";
        database.delete(GHOSTS_TABLE_NAME, where, args);
        database.delete(VERSIONS_TABLE_NAME, where, args);
    }

    protected Cursor queryChangeVersion(Bucket bucket) {
        String[] columns = { BUCKET_NAME_FIELD, CHANGE_VERSION_FIELD };
        String[] args = { bucket.getName() };
        return database.query(VERSIONS_TABLE_NAME, columns, "bucketName=?", args, null, null, null);
    }

    @Override
    public boolean hasChangeVersion(Bucket bucket) {
        Cursor cursor = queryChangeVersion(bucket);
        int count = cursor.getCount();
        cursor.close();
        return count > 0 && !getChangeVersion(bucket).equals("");
    }

    @Override
    public boolean hasChangeVersion(Bucket bucket, String cv) {
        // Logger.log(String.format("Do we have CV: %s", cv));
        String storedVersion = getChangeVersion(bucket);
        return storedVersion != null && storedVersion.equals(cv);
    }

    @Override
    public String getChangeVersion(Bucket bucket) {
        Cursor cursor = queryChangeVersion(bucket);
        String storedVersion = null;
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            storedVersion = cursor.getString(1);
        }
        cursor.close();
        return storedVersion;
    }

    @Override
    public void setChangeVersion(Bucket bucket, String cv) {
        if (TextUtils.isEmpty(cv)) {
            return;
        }

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
    }

    @Override
    public void saveGhost(Bucket bucket, Ghost ghost) {
        // CREATE/UPDATE
        ContentValues values = new ContentValues();
        values.put(BUCKET_NAME_FIELD, bucket.getName());
        values.put(VERSION_FIELD, ghost.getVersion());
        values.put(OBJECT_KEY_FIELD, ghost.getSimperiumKey());
        String payload = serializeGhostData(ghost);
        values.put(PAYLOAD_FIELD, payload);

        database.insertWithOnConflict(GHOSTS_TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public Ghost getGhost(Bucket bucket, String key) throws GhostMissingException {
        String[] columns = { BUCKET_NAME_FIELD, OBJECT_KEY_FIELD, VERSION_FIELD, PAYLOAD_FIELD };
        String where = "bucketName=? AND simperiumKey=?";
        String[] args = { bucket.getName(), key };

        try (Cursor cursor = database.query(GHOSTS_TABLE_NAME, columns, where, args, null, null, null)) {
            if (cursor.moveToFirst()) {
                JSONObject ghostData = new JSONObject(cursor.getString(3));
                return new Ghost(cursor.getString(1), cursor.getInt(2), ghostData);
            }
        } catch (org.json.JSONException e){
            // a corrupted ghost is effectively equal to a missing ghost
            // so pass through here and let the library request a new copy
        }

        throw(new GhostMissingException(String.format("Ghost %s does not exist for bucket %s", bucket.getName(), key)));
    }

    @Override
    public int getGhostVersion(Bucket bucket, String key) throws GhostMissingException {
        String[] columns = { VERSION_FIELD };
        String where = "bucketName=? AND simperiumKey=?";
        String[] args = { bucket.getName(), key };

        Cursor cursor = database.query(GHOSTS_TABLE_NAME, columns, where, args, null, null, "version DESC", "1");
        int version = -1;
        if(cursor.moveToFirst()){
            version = cursor.getInt(0);
        }
        cursor.close();
        if (version == -1) throw(new GhostMissingException(String.format("Ghost %s does not exist for bucket %s", bucket.getName(), key)));
        return version;
    }

    @Override
    public boolean hasGhost(Bucket bucket, String key) {
        try {
            getGhost(bucket, key);
        } catch (GhostMissingException e) {
            return false;
        }
        return true;
    }

    @Override
    public void deleteGhost(Bucket bucket, String key) {
        String where = "bucketName=? AND simperiumKey=?";
        String[] args = { bucket.getName(), key };
        database.delete(GHOSTS_TABLE_NAME, where, args);
    }

    private String serializeGhostData(Ghost ghost) {
        JSONObject json = ghost.getDiffableValue();
        if (json != null) {
            return json.toString();
        }
        return null;
    }

    private JSONObject deserializeGhostData(String data) {
        try {
            return new JSONObject(data);
        } catch (org.json.JSONException e) {
            Logger.log(String.format("Failed to deserialize ghost data %s", data), e);
            return null;
        }
    }

}