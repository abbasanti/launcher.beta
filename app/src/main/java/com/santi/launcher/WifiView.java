package com.santi.launcher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.view.View;

/** Ícono de red: barras de señal wifi, cable de red o sin conexión. */
class WifiView extends View {

    static final int SIN_CONEXION = -1;
    static final int CABLE = -2;

    private final ColorStateList colores;
    private final Paint pintura = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arco = new RectF();
    /** SIN_CONEXION, CABLE o nivel de señal de 1 a 4. */
    private int estado = SIN_CONEXION;

    WifiView(Context contexto, ColorStateList colores) {
        super(contexto);
        this.colores = colores;
        setDuplicateParentStateEnabled(true);
        pintura.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Lee el estado actual de la red y devuelve una descripción. */
    @SuppressWarnings("deprecation")
    String actualizar() {
        int nuevo = SIN_CONEXION;
        try {
            ConnectivityManager red = (ConnectivityManager)
                    getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activa = red.getActiveNetwork();
            NetworkCapabilities datos = activa == null ? null : red.getNetworkCapabilities(activa);
            if (datos != null && datos.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && !datos.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && !datos.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                // Con VPN se busca la conexión física que está por debajo.
                for (Network otra : red.getAllNetworks()) {
                    NetworkCapabilities c = red.getNetworkCapabilities(otra);
                    if (c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                            && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        datos = c;
                    }
                }
            }
            if (datos != null && datos.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                nuevo = CABLE;
            } else if (datos != null && datos.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wifi = (WifiManager)
                        getContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifi.getConnectionInfo();
                int nivel = WifiManager.calculateSignalLevel(info.getRssi(), 5);
                nuevo = Math.max(1, Math.min(4, nivel));
            }
        } catch (RuntimeException e) {
            nuevo = SIN_CONEXION;
        }
        if (nuevo != estado) {
            estado = nuevo;
            invalidate();
        }
        if (estado == CABLE) {
            return "red por cable";
        }
        return estado == SIN_CONEXION ? "sin conexión" : "señal wifi " + estado + " de 4";
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas lienzo) {
        int color = colores.getColorForState(getDrawableState(), colores.getDefaultColor());
        float u = getWidth() / 24f;
        pintura.setColor(color);
        pintura.setStrokeWidth(2.4f * u);

        if (estado == CABLE) {
            pintura.setStyle(Paint.Style.STROKE);
            lienzo.drawRect(8 * u, 3 * u, 16 * u, 9 * u, pintura);
            lienzo.drawRect(2 * u, 16 * u, 9 * u, 22 * u, pintura);
            lienzo.drawRect(15 * u, 16 * u, 22 * u, 22 * u, pintura);
            lienzo.drawLine(12 * u, 9 * u, 12 * u, 12.5f * u, pintura);
            lienzo.drawLine(5.5f * u, 12.5f * u, 18.5f * u, 12.5f * u, pintura);
            lienzo.drawLine(5.5f * u, 12.5f * u, 5.5f * u, 16 * u, pintura);
            lienzo.drawLine(18.5f * u, 12.5f * u, 18.5f * u, 16 * u, pintura);
            return;
        }

        int alfa = pintura.getAlpha();
        pintura.setStyle(Paint.Style.FILL);
        pintura.setAlpha(estado >= 1 ? alfa : alfa / 4);
        lienzo.drawCircle(12 * u, 19.5f * u, 1.8f * u, pintura);

        pintura.setStyle(Paint.Style.STROKE);
        float[] radios = {5f, 9.5f, 14f};
        for (int i = 0; i < radios.length; i++) {
            float r = radios[i] * u;
            arco.set(12 * u - r, 19.5f * u - r, 12 * u + r, 19.5f * u + r);
            pintura.setAlpha(estado >= i + 2 ? alfa : alfa / 4);
            lienzo.drawArc(arco, 225, 90, false, pintura);
        }

        if (estado == SIN_CONEXION) {
            pintura.setAlpha(alfa);
            lienzo.drawLine(4 * u, 4 * u, 20 * u, 21 * u, pintura);
        }
    }
}
