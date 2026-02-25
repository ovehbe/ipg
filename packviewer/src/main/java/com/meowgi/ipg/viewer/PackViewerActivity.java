package com.meowgi.ipg.viewer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackViewerActivity extends Activity {

    private static final Pattern DRAWABLE_PATTERN =
            Pattern.compile("drawable=\"([^\"]+)\"");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            buildUi();
        } catch (Exception e) {
            TextView tv = new TextView(this);
            tv.setText("Error: " + e.getMessage());
            tv.setPadding(40, 100, 40, 40);
            tv.setTextSize(14);
            setContentView(tv);
        }
    }

    private void buildUi() {
        String pkgName = getPackageName();
        boolean darkMode = pkgName.contains("white");

        int bgColor = darkMode ? 0xFF121212 : 0xFFFAFAFA;
        int textColor = darkMode ? 0xFFFFFFFF : 0xFF1A1A1A;
        int subtleColor = darkMode ? 0xFFAAAAAA : 0xFF888888;

        List<String> drawableNames = parseAppFilter();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);

        // Header
        TextView title = new TextView(this);
        try {
            title.setText(getApplicationInfo().loadLabel(getPackageManager()));
        } catch (Exception e) {
            title.setText(pkgName);
        }
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTextColor(textColor);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(dp(16), dp(48), dp(16), dp(4));
        root.addView(title);

        TextView count = new TextView(this);
        count.setText(drawableNames.size() + " icons");
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        count.setTextColor(subtleColor);
        count.setPadding(dp(16), 0, dp(16), dp(12));
        root.addView(count);

        if (drawableNames.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No icons found");
            empty.setTextColor(subtleColor);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(40), dp(16), dp(40));
            root.addView(empty);
        } else {
            GridView grid = new GridView(this);
            grid.setNumColumns(4);
            grid.setVerticalSpacing(dp(6));
            grid.setHorizontalSpacing(dp(6));
            grid.setPadding(dp(8), 0, dp(8), dp(60));
            grid.setClipToPadding(false);
            grid.setBackgroundColor(bgColor);

            String apkPath = null;
            try {
                apkPath = getApplicationInfo().sourceDir;
            } catch (Exception ignored) {}

            grid.setAdapter(new IconAdapter(drawableNames, subtleColor, apkPath));
            root.addView(grid, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        setContentView(root);

        // Color status/nav bars after setContentView
        try {
            getWindow().setStatusBarColor(bgColor);
            getWindow().setNavigationBarColor(bgColor);
            if (!darkMode) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {}
    }

    private List<String> parseAppFilter() {
        List<String> names = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(getAssets().open("appfilter.xml")));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = DRAWABLE_PATTERN.matcher(line);
                if (m.find()) {
                    names.add(m.group(1));
                }
            }
            reader.close();
        } catch (Exception ignored) {}
        return names;
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val,
                getResources().getDisplayMetrics());
    }

    private class IconAdapter extends BaseAdapter {
        private final List<String> names;
        private final int labelColor;
        private final String apkPath;

        IconAdapter(List<String> names, int labelColor, String apkPath) {
            this.names = names;
            this.labelColor = labelColor;
            this.apkPath = apkPath;
        }

        @Override public int getCount() { return names.size(); }
        @Override public Object getItem(int p) { return names.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView img;
            TextView lbl;

            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                img = (ImageView) cell.getChildAt(0);
                lbl = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(PackViewerActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(4), dp(6), dp(4), dp(6));

                img = new ImageView(PackViewerActivity.this);
                cell.addView(img, new LinearLayout.LayoutParams(dp(44), dp(44)));

                lbl = new TextView(PackViewerActivity.this);
                lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
                lbl.setGravity(Gravity.CENTER);
                lbl.setMaxLines(1);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(2);
                cell.addView(lbl, lp);
            }

            String name = names.get(position);
            img.setImageDrawable(loadIcon(name));

            String shortName = name;
            int sep = shortName.indexOf("__");
            if (sep > 0) shortName = shortName.substring(sep + 2);
            if (shortName.length() > 10) shortName = shortName.substring(0, 10);
            lbl.setText(shortName);
            lbl.setTextColor(labelColor);

            return cell;
        }

        private Drawable loadIcon(String name) {
            // Try standard resource lookup
            try {
                int resId = getResources().getIdentifier(
                        name, "drawable", getPackageName());
                if (resId != 0) {
                    return getResources().getDrawable(resId, null);
                }
            } catch (Exception ignored) {}

            // Fallback: read PNG directly from APK zip
            if (apkPath != null) {
                try {
                    ZipFile zip = new ZipFile(apkPath);
                    ZipEntry entry = zip.getEntry(
                            "res/drawable-nodpi-v4/" + name + ".png");
                    if (entry != null) {
                        InputStream is = zip.getInputStream(entry);
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        is.close();
                        zip.close();
                        if (bmp != null) {
                            return new BitmapDrawable(getResources(), bmp);
                        }
                    }
                    zip.close();
                } catch (Exception ignored) {}
            }
            return null;
        }
    }
}
