package com.supercambio.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class ShareProvider extends ContentProvider {

    public static final String AUTHORITY = "com.supercambio.app.share";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = resolve(uri);
        File f = new File(getContext().getCacheDir(), name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        // El tipo se saca del nombre REAL que se sirve (si no, el MIME y los bytes no
        // coinciden: se anunciaba JSON y se entregaba un PNG).
        String name = resolve(uri);
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "image/png";
    }

    // Nombre real del archivo: solo PNG/APK/JSON del caché y sin path traversal.
    private String resolve(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) return "compartir.png";
        if (name.contains("/") || name.contains("..")) return "compartir.png";
        if (!(name.endsWith(".png") || name.endsWith(".apk") || name.endsWith(".json"))) return "compartir.png";
        return name;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
