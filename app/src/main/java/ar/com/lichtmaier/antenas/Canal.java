package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.view.LayoutInflater;
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

	/** Obtiene un logo asociado al canal o a la cadena a la que pertenece.
	 *
	 * @return un recurso apuntando a un logo
	 */
	public int dameLogo()
	{
		int logo = 0;
		if(cadena != null) switch(cadena)
		{
			case "ABC":
				logo = R.drawable.logo_abc;
				break;
			case "CBS":
				logo = R.drawable.logo_cbs;
				break;
			case "CW":
			case "THE CW NETWORK":
			case "CW TELEVISION NETWOR":
				logo = R.drawable.logo_cw;
				break;
			case "FOX":
				logo = R.drawable.logo_fox;
				break;
			case "ION":
				logo = R.drawable.logo_ion;
				break;
			case "MYTV":
			case "MY NETWORK TV":
			case "MYNETWORK TV":
			case "MYNETWORKTV":
			case "MYNETWORK":
			case "MY NETWORK":
			case "MNT":
				logo = R.drawable.logo_mytv;
				break;
			case "NBC":
				logo = R.drawable.logo_nbc;
				break;
			case "PBS":
			case "PUBLIC BROADCASTING SERVICE":
				logo = R.drawable.logo_pbs;
				break;
			case "TELEMUNDO":
			case "TELMUNDO":
				logo = R.drawable.logo_telemundo;
				break;
			case "TRINITY BROADCASTING NETWORK":
			case "TBN":
				logo = R.drawable.logo_tbn;
				break;
			case "UNIMAS":
				logo = R.drawable.logo_unimas;
				break;
			case "UNIVISION":
			case "UNVISION":
				logo = R.drawable.logo_univision;
				break;
		}
		return logo;
	}

	/** Crea una vista que muestra información del canal.
	 *
	 * @param ctx un contexto
	 * @param parent el {@link android.view.ViewGroup} donde se insertará la vista
	 * @param conImagen si incluir ícono asociado al canal
	 * @return la vista
	 */
	public View dameViewCanal(Context ctx, ViewGroup parent, boolean conImagen)
	{
		View vc = ((LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.canal, parent, false);
		((TextView)vc.findViewById(R.id.nombre_canal)).setText(nombre);
		int logo = 0;
		if(conImagen)
		{
			logo = dameLogo();
			if(logo > 0)
			{
				ImageView iv = (ImageView)vc.findViewById(R.id.imagen_canal);
				iv.setImageResource(logo);
				iv.setContentDescription(cadena);
			}
		} else
		{
			vc.findViewById(R.id.imagen_canal).setVisibility(View.GONE);
		}
		TextView tv = (TextView)vc.findViewById(R.id.desc_canal);
		if(nombre == null || !nombre.startsWith("Canal "))
		{
			StringBuilder sb = new StringBuilder();
			sb.append(ctx.getString(R.string.channel_number, numero));
			if(numeroVirtual != null)
				sb.append(" (").append(numeroVirtual).append(")");
			if(logo == 0 && cadena != null && !cadena.equals("IND") && !cadena.equals("INDE") && !cadena.equals("NONE") && !cadena.isEmpty())
				sb.append(" - ").append(cadena);
			tv.setText(sb.toString());
		} else
		{
			tv.setVisibility(View.GONE);
		}
		return vc;
	}
}
