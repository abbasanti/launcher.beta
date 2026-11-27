package com.santi.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Preferencias guardadas del launcher. */
final class Config {

    static final String TEMA = "tema";
    static final String FONDO = "fondo";
    static final String ATENUAR = "atenuar";
    static final String TAMANO = "tamano";
    static final String NOMBRES = "nombres";
    static final String HORA = "hora";
    static final String FECHA = "fecha";
    static final String BOTON_AGREGAR = "boton_agregar";
    static final String PREGUNTADO = "predeterminado_preguntado";
    private static final String FAVORITAS = "favoritas";
    private static final String OCULTAS = "ocultas";

    private final SharedPreferences datos;

    Config(Context contexto) {
        datos = contexto.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    int numero(String clave, int inicial) {
        return datos.getInt(clave, inicial);
    }

    void poner(String clave, int valor) {
        datos.edit().putInt(clave, valor).apply();
    }

    /** null si el usuario todavía no eligió favoritas. */
    List<String> favoritas() {
        return lista(FAVORITAS);
    }

    void guardarFavoritas(List<String> valores) {
        datos.edit().putString(FAVORITAS, TextUtils.join("\n", valores)).apply();
    }

    List<String> ocultas() {
        List<String> valores = lista(OCULTAS);
        return valores == null ? new ArrayList<String>() : valores;
    }

    void guardarOcultas(List<String> valores) {
        datos.edit().putString(OCULTAS, TextUtils.join("\n", valores)).apply();
    }

    void restablecerApariencia() {
        datos.edit().remove(TEMA).remove(FONDO).remove(ATENUAR).remove(TAMANO)
                .remove(NOMBRES).remove(HORA).remove(FECHA).remove(BOTON_AGREGAR).apply();
    }

    private List<String> lista(String clave) {
        String guardado = datos.getString(clave, null);
        if (guardado == null) {
            return null;
        }
        List<String> valores = new ArrayList<String>();
        if (guardado.length() > 0) {
            valores.addAll(Arrays.asList(guardado.split("\n")));
        }
        return valores;
    }
}
