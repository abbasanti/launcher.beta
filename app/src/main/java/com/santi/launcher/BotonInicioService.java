package com.santi.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Hace que el botón Inicio abra Mi Launcher en equipos que ignoran el launcher
 * elegido. No lee el contenido de la pantalla.
 */
public class BotonInicioService extends AccessibilityService {

    /** Marca que la apertura reemplazó a la pantalla de inicio original. */
    static final String REDIRIGIDO = "redirigido";

    private final Set<String> iniciosAjenos = new HashSet<String>();
    private long ultimaApertura;

    static boolean activado(Context contexto) {
        String activos = Settings.Secure.getString(contexto.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (activos == null || Settings.Secure.getInt(contexto.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            return false;
        }
        ComponentName propio = new ComponentName(contexto, BotonInicioService.class);
        for (String nombre : activos.split(":")) {
            if (propio.equals(ComponentName.unflattenFromString(nombre))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        buscarIniciosAjenos();
    }

    /** Pantallas de inicio de otros launchers instalados. */
    private void buscarIniciosAjenos() {
        iniciosAjenos.clear();
        Intent inicio = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : getPackageManager().queryIntentActivities(inicio, 0)) {
            String paquete = info.activityInfo.packageName;
            // No se tocan la pantalla provisoria de Ajustes ni el asistente inicial.
            if (paquete.equals(getPackageName()) || paquete.endsWith(".settings")
                    || paquete.contains("setup")) {
                continue;
            }
            iniciosAjenos.add(paquete + "/" + info.activityInfo.name);
            if (info.activityInfo.targetActivity != null) {
                iniciosAjenos.add(paquete + "/" + info.activityInfo.targetActivity);
            }
        }
    }

    /** Primer método: interceptar la tecla antes de que la use el sistema. */
    @Override
    protected boolean onKeyEvent(KeyEvent evento) {
        if (evento.getKeyCode() != KeyEvent.KEYCODE_HOME) {
            return false;
        }
        if (evento.getAction() == KeyEvent.ACTION_UP && !evento.isCanceled()) {
            abrirLauncher(false);
        }
        return true;
    }

    /**
     * Segundo método, para equipos que no entregan la tecla Inicio: cuando
     * aparece la pantalla de inicio de otro launcher, se vuelve a este.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent evento) {
        if (evento.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || evento.getPackageName() == null || evento.getClassName() == null) {
            return;
        }
        String pantalla = evento.getPackageName() + "/" + evento.getClassName();
        if (iniciosAjenos.contains(pantalla)
                && SystemClock.elapsedRealtime() - ultimaApertura > 1000) {
            abrirLauncher(true);
        }
    }

    private void abrirLauncher(boolean redirigido) {
        ultimaApertura = SystemClock.elapsedRealtime();
        Intent intento = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(REDIRIGIDO, redirigido);
        try {
            // Con PendingIntent se evita la demora que aplica Android tras pulsar Inicio.
            PendingIntent.getActivity(this, redirigido ? 1 : 0, intento,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
        } catch (PendingIntent.CanceledException e) {
            startActivity(intento);
        }
    }

    @Override
    public void onInterrupt() {
        // No hay avisos de voz ni vibración que detener.
    }
}
