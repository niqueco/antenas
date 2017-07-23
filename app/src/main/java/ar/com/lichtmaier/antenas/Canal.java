package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.SoftReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Un canal que es transmitido por una {@link ar.com.lichtmaier.antenas.Antena}. */
public class Canal
{
	public final String nombre;
	public final String numero;
	public final String numeroVirtual;
	public final String cadena;
	public final char polarización;
	public final String ref;

	public Canal(String nombre, String numero, String numeroVirtual, String cadena, String polarización, String ref)
	{

		this.nombre = nombre;
		this.numero = numero;
		this.numeroVirtual = numeroVirtual;
		this.cadena = cadena;
		this.polarización = polarización == null ? '\0' : polarización.charAt(0);
		this.ref = ref;
	}

	/** Obtiene un logo asociado al canal o a la cadena a la que pertenece.
	 *
	 * @return un recurso apuntando a un logo
	 */
	public int dameLogo()
	{
		if(cadena == null)
			return 0;
		switch(cadena)
		{
			case "ABC":
				return R.drawable.logo_abc;
			case "CBS":
				return R.drawable.logo_cbs;
			case "CW":
			case "THE CW NETWORK":
			case "CW TELEVISION NETWOR":
				return R.drawable.logo_cw;
			case "FOX":
				return R.drawable.logo_fox;
			case "ION":
				return R.drawable.logo_ion;
			case "MYTV":
			case "MY NETWORK TV":
			case "MYNETWORK TV":
			case "MYNETWORKTV":
			case "MYNETWORK":
			case "MY NETWORK":
			case "MNT":
				return R.drawable.logo_mytv;
			case "NBC":
				return R.drawable.logo_nbc;
			case "PBS":
			case "PUBLIC BROADCASTING SERVICE":
				return R.drawable.logo_pbs;
			case "TELEMUNDO":
			case "TELMUNDO":
				return R.drawable.logo_telemundo;
			case "TRINITY BROADCASTING NETWORK":
			case "TBN":
				return R.drawable.logo_tbn;
			case "UNIMAS":
				return R.drawable.logo_unimas;
			case "UNIVISION":
			case "UNVISION":
				return R.drawable.logo_univision;
			default:
				return 0;
		}
	}

	private SoftReference<Bitmap> thumbnailRef;

	@Nullable
	public Bitmap dameThumbnail(Context context)
	{
		Bitmap bmp = null;
		if(thumbnailRef != null)
			bmp = thumbnailRef.get();
		if(bmp != null)
			return bmp;
		int logo = dameLogo();
		if(logo != 0)
		{
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = 2;
			bmp = BitmapFactory.decodeResource(context.getResources(), logo, options);
			thumbnailRef = new SoftReference<>(bmp);
		}
		return bmp;
	}

	/** Crea una vista que muestra información del canal.
	 *
	 * @param ctx un contexto
	 * @param parent el {@link ViewGroup} donde se insertará la vista
	 * @param conImagen si incluir ícono asociado al canal
	 * @param mostrarInfo si mostrar información técnica
	 * @param mostrarBanda si mostrar la banda (UHF/VHF)
	 * @return la vista
	 */
	public View dameViewCanal(Context ctx, ViewGroup parent, boolean conImagen, boolean mostrarInfo, boolean mostrarBanda)
	{
		View vc = LayoutInflater.from(ctx).inflate(R.layout.canal, parent, false);
		((TextView)vc.findViewById(R.id.nombre_canal)).setText(nombre);
		int logo = 0;
		ImageView imagenCanal = vc.findViewById(R.id.imagen_canal);
		if(conImagen)
		{
			logo = dameLogo();
			if(logo > 0)
			{
				imagenCanal.setImageResource(logo);
				imagenCanal.setContentDescription(cadena);
			} else
			{
				imagenCanal.setVisibility(View.INVISIBLE);
			}
		} else
		{
			imagenCanal.setVisibility(View.GONE);
		}
		TextView tv = vc.findViewById(R.id.desc_canal);
		if(nombre == null || !númeroEnElNombre())
		{
			StringBuilder sb = new StringBuilder();
			sb.append(ctx.getString(R.string.channel_number, numero));
			if(numeroVirtual != null)
				sb.append(" (").append(numeroVirtual).append(")");
			if(logo == 0 && cadena != null && !cadena.equals("IND") && !cadena.equals("INDE") && !cadena.equals("NONE") && cadena.length() != 0)
				sb.append(" - ").append(cadena);
			tv.setText(sb.toString());
		} else
		{
			tv.setVisibility(View.GONE);
		}
		if(mostrarInfo && polarización != '\0')
		{
			TextView it = vc.findViewById(R.id.info_tecnica);
			StringBuilder str = new StringBuilder();
			if(mostrarBanda && numero != null)
				str.append(Integer.parseInt(numero.replaceAll("[A-Z]$|\\..*$", "")) <= 13 ? "VHF" : "UHF").append('\n');
			str.append(damePolarización(ctx));
			it.setText(str);
			it.setVisibility(View.VISIBLE);
		}
		return vc;
	}

	final private static Pattern patternCanal = Pattern.compile("(?:Canal|Channel) (\\d+)$");

	boolean númeroEnElNombre()
	{
		if(nombre != null)
		{
			Matcher m = patternCanal.matcher(nombre);
			if(m.find() && m.group(1).equals(numero))
				return true;
		}
		return false;
	}

	private String damePolarización(Context context)
	{
		int res;
		switch(polarización)
		{
			case 'H':
				res = R.string.polarización_horizontal;
				break;
			case 'V':
				res = R.string.polarización_vertical;
				break;
			case 'M':
				res = R.string.polarización_mezclada;
				break;
			case 'D':
				res = R.string.polarización_dual;
				break;
			default:
				return null;
		}
		return context.getString(res);
	}

	@Override
	public String toString()
	{
		return nombre;
	}
}
