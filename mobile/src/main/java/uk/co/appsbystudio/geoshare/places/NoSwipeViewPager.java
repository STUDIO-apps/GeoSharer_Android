package uk.co.appsbystudio.geoshare.places;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class NoSwipeViewPager extends ViewPager{

    private boolean enabled = false;

    public NoSwipeViewPager(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.enabled = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return this.enabled && super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return this.enabled && super.onInterceptTouchEvent(ev);
    }

    public void setPagingEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
