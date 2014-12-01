package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.Serializable;

/** Un canal que es transmitido por una {@link ar.com.lichtmaier.antenas.Antena}. */
public class Canal implements Serializable
{
	public final String nombre;
	public final String numero;
	public final String numeroVirtual;
	public final String cadena;
	public final String ref;

	public Canal(String nombre, String numero, String numeroVirtual, String cadena, String ref)
	{

		this.nombre = nombre;
		this.numero = numero;
		this.numeroVirtual = numeroVirtual;
		this.cadena = cadena;
		this.ref = ref;
	}
}
