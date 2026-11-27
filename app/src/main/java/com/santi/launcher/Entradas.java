package com.santi.launcher;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entradas de la TV: HDMI, AV (RCA), antena y demás. */
final class Entradas {

    static final class Entrada {
        final String nombre;
        final Intent abrir;
        private final int tipo;

        private Entrada(String nombre, int tipo, Intent abrir) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.abrir = abrir;
        }
    }

    private Entradas() {
    }

    /** Lista vacía si el equipo no tiene entradas o no las comparte. */
    static List<Entrada> buscar(Context contexto) {
        List<Entrada> lista = new ArrayList<Entrada>();
        try {
            TvInputManager tv = (TvInputManager) contexto.getSystemService(Context.TV_INPUT_SERVICE);
            if (tv == null) {
                return lista;
            }
            List<TvInputInfo> todas = tv.getTvInputList();

            // Si un aparato conectado se identifica (HDMI-CEC), reemplaza a su puerto.
            Set<String> puertosOcupados = new HashSet<String>();
            for (TvInputInfo info : todas) {
                if (info.getParentId() != null && !oculta(contexto, info)) {
                    puertosOcupados.add(info.getParentId());
                }
            }

            boolean hayAntena = false;
            for (TvInputInfo info : todas) {
                if (oculta(contexto, info)) {
                    continue;
                }
                if (info.isPassthroughInput()) {
                    if (puertosOcupados.contains(info.getId())) {
                        continue;
                    }
                    String nombre = String.valueOf(info.loadLabel(contexto));
                    String tipo = nombreTipo(info.getType());
                    if (tipo != null && !nombre.toUpperCase().contains(tipo.split(" ")[0].toUpperCase())) {
                        nombre += " (" + tipo + ")";
                    }
                    nombre += estado(tv, info.getId());
                    Intent abrir = new Intent(Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(info.getId()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    lista.add(new Entrada(nombre, info.getType(), abrir));
                } else if (info.getType() == TvInputInfo.TYPE_TUNER) {
                    hayAntena = true;
                }
            }
            if (hayAntena) {
                Intent abrir = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                lista.add(new Entrada("TV (antena o cable)", TvInputInfo.TYPE_TUNER, abrir));
            }
        } catch (RuntimeException e) {
            // Algunos equipos no implementan el sistema de entradas.
        }
        Collections.sort(lista, new Comparator<Entrada>() {
            @Override
            public int compare(Entrada a, Entrada b) {
                // La antena primero; luego HDMI y el resto, por nombre.
                int orden = prioridad(a.tipo) - prioridad(b.tipo);
                return orden != 0 ? orden : a.nombre.compareToIgnoreCase(b.nombre);
            }
        });
        return lista;
    }

    private static int prioridad(int tipo) {
        if (tipo == TvInputInfo.TYPE_TUNER) {
            return 0;
        }
        return tipo == TvInputInfo.TYPE_HDMI ? 1 : 2;
    }

    private static String nombreTipo(int tipo) {
        switch (tipo) {
            case TvInputInfo.TYPE_HDMI:
                return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE:
                return "AV / RCA";
            case TvInputInfo.TYPE_COMPONENT:
                return "Componentes";
            case TvInputInfo.TYPE_SVIDEO:
                return "S-Video";
            case TvInputInfo.TYPE_SCART:
                return "SCART";
            case TvInputInfo.TYPE_VGA:
                return "VGA";
            case TvInputInfo.TYPE_DVI:
                return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT:
                return "DisplayPort";
            default:
                return null;
        }
    }

    private static String estado(TvInputManager tv, String id) {
        try {
            int estado = tv.getInputState(id);
            if (estado == TvInputManager.INPUT_STATE_DISCONNECTED) {
                return "  ·  sin conexión";
            }
            if (estado == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) {
                return "  ·  en espera";
            }
            return "  ·  conectado";
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Entradas que el usuario ocultó en la TV (Android 7 o superior). */
    private static boolean oculta(Context contexto, TvInputInfo info) {
        try {
            return (Boolean) TvInputInfo.class.getMethod("isHidden", Context.class)
                    .invoke(info, contexto);
        } catch (Exception e) {
            return false;
        }
    }
}
