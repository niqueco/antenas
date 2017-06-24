package ar.com.lichtmaier.antenas;

import android.arch.lifecycle.LifecycleFragment;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import org.gavaghan.geodesy.GlobalCoordinates;

public class CanalesMapaFragment extends LifecycleFragment
{
	private Antena antena;
	private MapaActivity callback;
	private View selectedView;
	private Canal canalSeleccionadoInicialmente;

	static CanalesMapaFragment crear(Antena antena, Canal canal)
	{
		CanalesMapaFragment fr = new CanalesMapaFragment();
		Bundle args = new Bundle();
		args.putInt("país", antena.país.ordinal());
		args.putInt("index", antena.index);
		fr.setArguments(args);
		fr.canalSeleccionadoInicialmente = canal;
		return fr;
	}

	@Override
	public void onAttach(Context context)
	{
		super.onAttach(context);
		callback = (MapaActivity)context;
	}

	private void seleccionar(View v)
	{
		if(selectedView != null)
		{
			selectedView.setSelected(false);
			//noinspection RedundantCast
			((FrameLayout)selectedView).setForeground(null);
		}
		selectedView = v;
		v.setSelected(true);
		//noinspection RedundantCast
		((FrameLayout)selectedView).setForeground(new ColorDrawable(0x55eeeeee));
		v.requestRectangleOnScreen(new Rect(0, 0, v.getWidth(), v.getHeight()));
		callback.canalSeleccionado(antena, (Canal)v.getTag());
	}

	private void seleccionar(Canal canal)
	{
		View rootView = getView();
		assert rootView != null;
		View v = rootView.findViewWithTag(canal);
		seleccionar(v);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
	{
		País país = País.values()[getArguments().getInt("país")];
		int index = getArguments().getInt("index");
		antena = Antena.dameAntena(getActivity(), país, index);

		boolean hayImágenes = antena.hayImágenes();
		ContextThemeWrapper ctx = new ContextThemeWrapper(getActivity(), R.style.InfoMapa);
		final ViewGroup v = (ViewGroup)inflater.inflate(R.layout.info_mapa, container, false);
		TextView tv = v.findViewById(R.id.antena_desc);
		if(tv != null)
		{
			if(antena.descripción == null)
				tv.setVisibility(View.GONE);
			else
				tv.setText(antena.descripción);
		}
		TextView distView = v.findViewById(R.id.antena_dist);
		if(distView != null)
			((MapaActivity)getActivity()).getLocation().observe(this, location -> ponerDistancia(location, distView, antena.descripción != null));
		View viewCanalASeleccionar = null;
		final int canalSeleccionadoPos = savedInstanceState != null ? savedInstanceState.getInt("canal", -1) : -1;
		ViewGroup l = v.findViewById(R.id.lista_canales);
		int n;
		if(l instanceof TableLayout)
		{
			TypedArray arr = getActivity().getTheme().obtainStyledAttributes(new int[]{R.attr.selectableItemBackground});
			int selectableItemBackground = arr.getResourceId(0, -1);
			arr.recycle();
			n = antena.canales.size();
			int ncolumns;
			if(l.getParent().getParent().getClass() != ScrollView.class)
			{
				int filas = n == 2 ? 1 : n > 6 && antena.descripción == null ? 3 : 2;

				ncolumns = (n + 1) / filas;
				if(ncolumns == 0)
					ncolumns++;
			} else
			{
				ncolumns = 1;
			}
			for(int i = 0; i < (n + ncolumns - 1) / ncolumns; i++)
			{
				TableRow row = new TableRow(ctx);

				for(int j = 0; j < ncolumns && (i * ncolumns + j) < antena.canales.size(); j++)
				{
					int posCanal = i * ncolumns + j;
					Canal canal = antena.canales.get(posCanal);
					View vc = canal.dameViewCanal(ctx, row, hayImágenes, false, false);

					FrameLayout fl = new FrameLayout(getContext());
					fl.addView(vc);
					vc = fl;

					if(antena.país == País.US && canal.ref != null)
					{
						if(viewCanalASeleccionar == null && (canalSeleccionadoPos == -1 || posCanal == canalSeleccionadoPos))
							viewCanalASeleccionar = vc;

						vc.setClickable(true);
						vc.setFocusable(true);
						vc.setTag(canal);
						//noinspection deprecation
						vc.setBackgroundResource(selectableItemBackground);
						vc.setOnClickListener(this::seleccionar);
					}
					vc.setMinimumHeight((int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics()));
					if(j > 0)
						vc.setPadding((int)getResources().getDimension(vc.getPaddingLeft() + R.dimen.paddingColumnasInfoMapa), vc.getPaddingTop(), vc.getPaddingRight(), vc.getPaddingBottom());
					row.addView(vc);
				}

				l.addView(row);
			}
		} else
		{
			n = Math.min(antena.canales.size(), 4);
			for(Canal canal : antena.canales)
				l.addView(canal.dameViewCanal(ctx, l, hayImágenes, false, false));
		}
		if(n < antena.canales.size())
		{
			tv = new TextView(ctx);
			tv.setText(ctx.getString(R.string.some_more, antena.canales.size() - n));
			tv.setLayoutParams(
					(l instanceof TableLayout)
							? new TableLayout.LayoutParams()
							: new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
			);
			tv.setGravity(Gravity.CENTER);
			l.addView(tv);
		}
		ViewTreeObserver vto = v.getViewTreeObserver();
		vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener()
		{
			@Override
			public boolean onPreDraw()
			{
				MapaFragment mfr = (MapaFragment)getFragmentManager().findFragmentById(R.id.container);

				if(!mfr.mapaInicializado())
					return true;

				v.getViewTreeObserver().removeOnPreDrawListener(this);

				mfr.configurarPaddingMapa(v);

				return true;
			}
		});
		if(viewCanalASeleccionar != null)
			seleccionar(viewCanalASeleccionar);
		return v;
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		if(canalSeleccionadoInicialmente != null)
		{
			seleccionar(canalSeleccionadoInicialmente);
			canalSeleccionadoInicialmente = null;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);

		if(selectedView != null)
			//noinspection SuspiciousMethodCalls
			outState.putInt("canal", antena.canales.indexOf(selectedView.getTag()));

	}

	private void ponerDistancia(Location location, TextView tv, boolean comentario)
	{
		Lugar l = Lugar.actual.getValue();
		double distancia = antena.distanceTo(l == null ? new GlobalCoordinates(location.getLatitude(), location.getLongitude()) : l.coords);
		if(distancia < 100000)
		{
			int res = comentario ? R.string.dist_away_comentario : R.string.dist_away_solo;
			tv.setText(getString(res, Formatos.formatDistance(getActivity(), distancia)));
			tv.setVisibility(View.VISIBLE);
		} else
		{
			tv.setVisibility(View.GONE);
		}
	}
}
