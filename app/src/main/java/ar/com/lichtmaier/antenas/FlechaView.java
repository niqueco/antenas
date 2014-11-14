package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.util.AttributeSet;
import android.view.View;

public class FlechaView extends View
{
	private double ángulo;
	final private Paint pinturaFlecha, pinturaBorde;
	private float cx, cy, z;
	private float[] líneasFlecha;

	public FlechaView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		TypedArray values = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ArrowView, 0, 0);
		try
		{
			pinturaFlecha = new Paint(Paint.ANTI_ALIAS_FLAG);
			pinturaFlecha.setColor(values.getColor(R.styleable.ArrowView_colorFlecha, Color.BLACK));
			pinturaFlecha.setStrokeCap(Cap.ROUND);
			pinturaBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
			pinturaBorde.setColor(values.getColor(R.styleable.ArrowView_colorDial, Color.BLACK));
			pinturaBorde.setStyle(Paint.Style.STROKE);
		} finally
		{
			values.recycle();
		}
		float density = getResources().getDisplayMetrics().density;
		int z = (int)(100 * density);
		setMinimumHeight(z);
		setMinimumWidth(z);
	}

	public double getÁngulo()
	{
		return ángulo;
	}

	public void setÁngulo(double ángulo)
	{
		double antes = this.ángulo;
		this.ángulo = ángulo;
		if(antes != ángulo)
			invalidate();
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		pinturaFlecha.setStrokeWidth(6f * (float)w / 100f);
		pinturaBorde.setStrokeWidth(2f * (float)w / 100f);
		cx = getWidth() / 2.0f;
		cy = getHeight() / 2.0f;
		float maxpadding = Math.max(Math.max(getPaddingLeft(), getPaddingRight()), Math.max(getPaddingTop(), getPaddingBottom()));
		z = .8f * Math.min(cx, cy) - maxpadding;
		líneasFlecha = new float[] {
				0, w / 5f, 0, -z,
				0, -z, w / 10f, w / 10f - z,
				0, -z, -w / 10f, w / 10f - z
		};
		Compat.disableHardwareAccelerationForLineCaps(this);
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		float density = getResources().getDisplayMetrics().density;
		int w, h;
		w = h = (int)(100 * density);
		if(View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY)
			w = View.MeasureSpec.getSize(widthMeasureSpec);
		else if(View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.AT_MOST)
			w = Math.min(View.MeasureSpec.getSize(widthMeasureSpec), w);
		if(View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY)
			h = View.MeasureSpec.getSize(heightMeasureSpec);
		else if(View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.AT_MOST)
			h = Math.min(View.MeasureSpec.getSize(heightMeasureSpec), h);
		w = h = Math.min(h, w);
		setMeasuredDimension(w, h);
	}
	
	@Override
	protected void onDraw(Canvas canvas)
	{
		canvas.save();
		canvas.translate(cx, cy);
		canvas.rotate((float)ángulo);
		canvas.drawCircle(0, 0, z + pinturaFlecha.getStrokeWidth() * .75f, pinturaBorde);
		canvas.drawLines(líneasFlecha, pinturaFlecha);
		canvas.restore();
	}
}
