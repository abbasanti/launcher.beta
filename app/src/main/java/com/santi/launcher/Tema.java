package com.santi.launcher;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;

/** Colores de la interfaz según el modo claro u oscuro. */
final class Tema {

    static final int ACENTO = 0xFF1FB6FF;

    final boolean claro;
    final int texto;
    final int textoSecundario;
    final int sombra;
    final int boton;
    final int panel;
    final int velo;
    final int tarjeta;
    final int seleccionado = 0xFF0A72BD;
    final int bordeFoco;

    private Tema(boolean claro) {
        this.claro = claro;
        texto = claro ? 0xFF0B1B2E : Color.WHITE;
        textoSecundario = claro ? 0xFF3D5068 : 0xFFB8C7DA;
        sombra = claro ? Color.WHITE : Color.BLACK;
        boton = claro ? 0xD9FFFFFF : 0xA6081C36;
        panel = claro ? 0xFAF2F6FB : 0xFA0A1626;
        velo = claro ? 0xCCEEF4FA : 0xB8041022;
        tarjeta = claro ? 0xFFFFFFFF : 0xFF1D2635;
        bordeFoco = claro ? 0xFF0B1B2E : Color.WHITE;
    }

    /** modo: 0 = según el sistema, 1 = claro, 2 = oscuro. */
    static boolean esClaro(int modo, Configuration configuracion) {
        if (modo != 0) {
            return modo == 1;
        }
        int noche = configuracion.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        // Muchas TV no informan el modo: en ese caso se usa el oscuro.
        return noche == Configuration.UI_MODE_NIGHT_NO;
    }

    static Tema crear(boolean claro) {
        return new Tema(claro);
    }

    /** Color de íconos y textos de un botón según su estado. */
    ColorStateList coloresBoton() {
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{android.R.attr.state_selected},
                        new int[]{}},
                new int[]{Color.WHITE, Color.WHITE, texto});
    }

    AlertDialog.Builder dialogo(android.content.Context contexto) {
        return new AlertDialog.Builder(contexto, claro
                ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }
}
