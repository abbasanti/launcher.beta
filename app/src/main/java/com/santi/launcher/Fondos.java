package com.santi.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Fondos incluidos e imagen propia. */
final class Fondos {

    static final String[] NOMBRES = {
            "Paisaje", "Azul", "Atardecer", "Bosque", "Liso", "Imagen propia"
    };
    static final int PROPIO = 5;

    /** Pares de colores (arriba, abajo) para modo oscuro y claro. */
    private static final int[][][] DEGRADADOS = {
            {{0xFF0B3D7A, 0xFF04101F}, {0xFFBFE0FF, 0xFFEFF7FF}},
            {{0xFF4A2262, 0xFF9A4327}, {0xFFFFD9C2, 0xFFE9DDFF}},
            {{0xFF0F4A3C, 0xFF04140F}, {0xFFCDEFD9, 0xFFF1FAF0}},
            {{0xFF0E1116, 0xFF0E1116}, {0xFFEEF1F5, 0xFFEEF1F5}}
    };

    private Fondos() {
    }

    private static File archivo(Context contexto) {
        return new File(contexto.getFilesDir(), "fondo_propio.jpg");
    }

    @SuppressWarnings("deprecation")
    static Drawable crear(Context contexto, int indice, boolean claro) {
        if (indice == PROPIO) {
            Bitmap imagen = BitmapFactory.decodeFile(archivo(contexto).getAbsolutePath());
            if (imagen != null) {
                return new BitmapDrawable(contexto.getResources(), imagen);
            }
        } else if (indice >= 1 && indice <= DEGRADADOS.length) {
            return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    DEGRADADOS[indice - 1][claro ? 1 : 0]);
        }
        return contexto.getResources().getDrawable(claro ? R.drawable.fondo_claro : R.drawable.fondo);
    }

    /** Copia la imagen elegida, reducida al tamaño de la pantalla. */
    static void importar(Context contexto, Uri origen) throws IOException {
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inJustDecodeBounds = true;
        leer(contexto, origen, opciones);
        if (opciones.outWidth <= 0 || opciones.outHeight <= 0) {
            throw new IOException("No es una imagen");
        }
        int muestra = 1;
        while (opciones.outWidth / (muestra * 2) >= 1920 && opciones.outHeight / (muestra * 2) >= 1080
                || Math.max(opciones.outWidth, opciones.outHeight) / muestra > 4096) {
            muestra *= 2;
        }
        opciones.inJustDecodeBounds = false;
        opciones.inSampleSize = muestra;
        Bitmap imagen = leer(contexto, origen, opciones);
        if (imagen == null) {
            throw new IOException("No se pudo leer la imagen");
        }
        File temporal = new File(contexto.getFilesDir(), "fondo_nuevo.jpg");
        OutputStream salida = new FileOutputStream(temporal);
        try {
            imagen.compress(Bitmap.CompressFormat.JPEG, 92, salida);
        } finally {
            salida.close();
            imagen.recycle();
        }
        if (!temporal.renameTo(archivo(contexto))) {
            throw new IOException("No se pudo guardar la imagen");
        }
    }

    private static Bitmap leer(Context contexto, Uri origen, BitmapFactory.Options opciones)
            throws IOException {
        InputStream entrada = contexto.getContentResolver().openInputStream(origen);
        if (entrada == null) {
            throw new IOException("No se pudo abrir la imagen");
        }
        try {
            return BitmapFactory.decodeStream(entrada, null, opciones);
        } finally {
            entrada.close();
        }
    }
}
