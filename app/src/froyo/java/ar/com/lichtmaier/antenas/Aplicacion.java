package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.app.Application;

@SuppressWarnings("UnusedParameters")
public class Aplicacion extends Application
{
	void reportActivityStart(Activity act) { }

	void reportActivityStop(Activity act) { }

	void mandarEvento(String categoría, String acción) { }
}
