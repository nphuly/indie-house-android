package house.indie.legacy;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bottom navigation matching `.mobile-nav` on the web; also renders in Android Studio's preview. */
public final class NavigationBar extends LinearLayout {
    public interface Listener { void onTabSelected(int tab, boolean again); }

    private static final int[] NAMES = {R.string.nav_home, R.string.nav_search, R.string.nav_browse, R.string.nav_library};

    static String name(Context context, int tab) { return context.getString(NAMES[tab]); }
    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_search,
            R.drawable.ic_compass, R.drawable.ic_library};
    private final ImageView[] icons = new ImageView[NAMES.length];
    private final TextView[] labels = new TextView[NAMES.length];
    private final View[] halos = new View[NAMES.length];
    private Listener listener;
    private int selected = -1;

    public NavigationBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        build();
        select(0);
    }

    public NavigationBar(Context context, int selected, Listener listener) {
        super(context);
        this.listener = listener;
        build();
        select(selected);
    }

    private void build() {
        Theme.load(getContext());
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.BG);
        View line = new View(getContext());
        line.setBackgroundColor(Theme.LINE_SOFT);
        addView(line, new LayoutParams(-1, 1));
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < NAMES.length; i++) {
            final int target = i;
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackgroundDrawable(new ColorDrawable(0));
            FrameLayout iconFrame = new FrameLayout(getContext());
            halos[i] = new View(getContext());
            halos[i].setBackgroundDrawable(Widgets.shape(getContext(), Theme.PRESSED, 2));
            halos[i].setVisibility(INVISIBLE);
            iconFrame.addView(halos[i], new FrameLayout.LayoutParams(-1, -1));
            icons[i] = new ImageView(getContext());
            icons[i].setImageResource(ICONS[i]);
            icons[i].setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = Widgets.dp(getContext(), 24);
            iconFrame.addView(icons[i], new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
            item.addView(iconFrame, new LayoutParams(Widgets.dp(getContext(), 40), Widgets.dp(getContext(), 30)));
            labels[i] = new TextView(getContext());
            labels[i].setText(NAMES[i]);
            labels[i].setTextSize(11);
            labels[i].setGravity(Gravity.CENTER);
            labels[i].setSingleLine(true);
            item.addView(labels[i], new LayoutParams(-1, -2));
            final View halo = halos[i];
            item.setOnTouchListener(new OnTouchListener() {
                @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                    int action = event.getActionMasked();
                    if (action == android.view.MotionEvent.ACTION_DOWN) halo.setVisibility(VISIBLE);
                    else if (action == android.view.MotionEvent.ACTION_UP
                            || action == android.view.MotionEvent.ACTION_CANCEL) halo.setVisibility(INVISIBLE);
                    return false;
                }
            });
            item.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) {
                    boolean again = target == selected;
                    select(target);
                    if (listener != null) listener.onTabSelected(target, again);
                }
            });
            tabs.addView(item, new LayoutParams(0, Widgets.dp(getContext(), 56), 1));
        }
        addView(tabs);
    }

    void select(int tab) {
        selected = tab;
        for (int i = 0; i < NAMES.length; i++) {
            boolean active = i == tab;
            icons[i].setColorFilter(active ? Theme.ACCENT : Theme.MUTED, PorterDuff.Mode.SRC_IN);
            labels[i].setTextColor(active ? Theme.ACCENT : Theme.MUTED);
            labels[i].setTypeface(active ? Theme.semibold : Theme.regular);
            ((View) icons[i].getParent().getParent()).setContentDescription(active
                    ? getContext().getString(R.string.selected_suffix, name(getContext(), i)) : name(getContext(), i));
            ((View) icons[i].getParent().getParent()).setSelected(active);
        }
    }
}
