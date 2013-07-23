/**
 * A ListAdapter that interfaces with an ObjectCursor
 */
package com.simperium.widget;

import com.simperium.client.Syncable;
import com.simperium.client.Bucket.ObjectCursor;

import android.content.Context;
import android.widget.CursorAdapter;
import android.view.View;
import android.database.Cursor;

public abstract class BucketCursorAdapter<T extends Syncable> extends CursorAdapter {

    private ObjectCursor<T> mCursor;

    public BucketCursorAdapter(Context context, ObjectCursor<T> cursor){
        super(context, cursor);
        mCursor = cursor;
    }

    public BucketCursorAdapter(Context context, ObjectCursor<T> cursor, int flags){
        super(context, cursor, flags);
        mCursor = cursor;
    }

    public void changeCursor(ObjectCursor<T> cursor){
        super.changeCursor(cursor);
        mCursor = cursor;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor){
        bindView(view, context, mCursor.getObject());
    }

    abstract public void bindView(View view, Context context, T object);

    public T getItem(int position){
        mCursor.moveToPosition(position);
        return mCursor.getObject();
    }

}