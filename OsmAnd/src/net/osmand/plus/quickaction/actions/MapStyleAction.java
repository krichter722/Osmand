package net.osmand.plus.quickaction.actions;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dialogs.ConfigureMapMenu;
import net.osmand.plus.dialogs.SelectMapStyleQuickActionBottomSheet;
import net.osmand.plus.openseamapsplugin.NauticalMapsPlugin;
import net.osmand.plus.quickaction.QuickAction;
import net.osmand.plus.quickaction.QuickActionItemTouchHelperCallback;
import net.osmand.plus.quickaction.QuickActionListFragment;
import net.osmand.plus.quickaction.SwitchableAction;
import net.osmand.plus.render.RendererRegistry;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.render.RenderingRulesStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class MapStyleAction extends SwitchableAction<String> {

	public static final int TYPE = 14;

	private final static String KEY_STYLES = "styles";
	public static final String KEY_DIALOG = "dialog";

	public MapStyleAction() {
		super(TYPE);
	}

	public MapStyleAction(QuickAction quickAction) {
		super(quickAction);
	}

	@Override
	public void execute(MapActivity activity) {

		ArrayList<String> mapStyles = (ArrayList<String>) getFilteredStyles();
		boolean showBottomSheetStyles = Boolean.valueOf(getParams().get(KEY_DIALOG));
		if (showBottomSheetStyles) {
			SelectMapStyleQuickActionBottomSheet fragment = new SelectMapStyleQuickActionBottomSheet();

			Bundle args = new Bundle();
			args.putStringArrayList("test", mapStyles);
			args.putInt("type", MapStyleAction.TYPE);
			fragment.setArguments(args);
			fragment.show(activity.getSupportFragmentManager(),
					SelectMapStyleQuickActionBottomSheet.TAG);
			return;
		}
		String curStyle = activity.getMyApplication().getSettings().RENDERER.get();
		int index = mapStyles.indexOf(curStyle);
		String nextStyle = mapStyles.get(0);

		if (index >= 0 && index + 1 < mapStyles.size()) {
			nextStyle = mapStyles.get(index + 1);
		}

		RenderingRulesStorage loaded = activity.getMyApplication()
				.getRendererRegistry().getRenderer(nextStyle);

		if (loaded != null) {

			OsmandMapTileView view = activity.getMapView();
			view.getSettings().RENDERER.set(nextStyle);

			activity.getMyApplication().getRendererRegistry().setCurrentSelectedRender(loaded);
			ConfigureMapMenu.refreshMapComplete(activity);

			Toast.makeText(activity, activity.getString(R.string.quick_action_map_style_switch, nextStyle), Toast.LENGTH_SHORT).show();

		} else {

			Toast.makeText(activity, R.string.renderer_load_exception, Toast.LENGTH_SHORT).show();
		}
	}

	public List<String> getFilteredStyles() {

		List<String> filtered = new ArrayList<>();
		boolean enabled = OsmandPlugin.getEnabledPlugin(NauticalMapsPlugin.class) != null;

		if (enabled) return loadListFromParams();
		else {

			for (String style : loadListFromParams()) {

				if (!style.equals(RendererRegistry.NAUTICAL_RENDER)) {
					filtered.add(style);
				}
			}
		}

		return filtered;
	}

	@Override
	protected int getAddBtnText() {
		return R.string.quick_action_map_style_action;
	}

	@Override
	protected int getDiscrHint() {
		return R.string.quick_action_page_list_descr;
	}

	@Override
	protected int getDiscrTitle() {
		return R.string.quick_action_map_styles;
	}

	@Override
	protected String getListKey() {
		return KEY_STYLES;
	}

	@Override
	protected View.OnClickListener getOnAddBtnClickListener(final MapActivity activity, final Adapter adapter) {
		return new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				AlertDialog.Builder bld = new AlertDialog.Builder(activity);
				bld.setTitle(R.string.renderers);

				final OsmandApplication app = activity.getMyApplication();
				final List<String> visibleNamesList = new ArrayList<>();
				final ArrayList<String> items = new ArrayList<>(app.getRendererRegistry().getRendererNames());
				final boolean nauticalPluginDisabled = OsmandPlugin.getEnabledPlugin(NauticalMapsPlugin.class) == null;

				Iterator<String> iterator = items.iterator();
				while (iterator.hasNext()) {
					String item = iterator.next();
					if (nauticalPluginDisabled && item.equals(RendererRegistry.NAUTICAL_RENDER)) {
						iterator.remove();
					} else {
						String translation = RendererRegistry.getTranslatedRendererName(activity, item);
						visibleNamesList.add(translation != null ? translation
								: item.replace('_', ' ').replace('-', ' '));
					}
				}

				final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(activity, R.layout.dialog_text_item);

				arrayAdapter.addAll(visibleNamesList);
				bld.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {

						String renderer = items.get(i);
						RenderingRulesStorage loaded = app.getRendererRegistry().getRenderer(renderer);

						if (loaded != null) {

							adapter.addItem(renderer, activity);
						}

						dialogInterface.dismiss();
					}
				});

				bld.setNegativeButton(R.string.shared_string_dismiss, null);
				bld.show();
			}
		};
	}

	@Override
	public void drawUI(ViewGroup parent, final MapActivity activity) {

		View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.quick_action_switchable_action, parent, false);


		SwitchCompat showDialog = (SwitchCompat) view.findViewById(R.id.saveButton);
		if (!getParams().isEmpty()) {
			showDialog.setChecked(Boolean.valueOf(getParams().get(KEY_DIALOG)));
		}

		final RecyclerView list = (RecyclerView) view.findViewById(R.id.list);

		final QuickActionItemTouchHelperCallback touchHelperCallback = new QuickActionItemTouchHelperCallback();
		final ItemTouchHelper touchHelper = new ItemTouchHelper(touchHelperCallback);

		final Adapter adapter = new Adapter(new QuickActionListFragment.OnStartDragListener() {
			@Override
			public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
				touchHelper.startDrag(viewHolder);
			}
		});

		touchHelperCallback.setItemMoveCallback(adapter);
		touchHelper.attachToRecyclerView(list);

		if (!getParams().isEmpty()) {
			adapter.addItems(loadListFromParams());
		}

		list.setAdapter(adapter);

		TextView dscrTitle = (TextView) view.findViewById(R.id.textDscrTitle);
		TextView dscrHint = (TextView) view.findViewById(R.id.textDscrHint);
		Button addBtn = (Button) view.findViewById(R.id.btnAdd);

		dscrTitle.setText(parent.getContext().getString(getDiscrTitle()) + ":");
		dscrHint.setText(getDiscrHint());
		addBtn.setText(getAddBtnText());
		addBtn.setOnClickListener(getOnAddBtnClickListener(activity, adapter));

		parent.addView(view);
	}

	@Override
	protected void saveListToParams(List<String> styles) {
		getParams().put(getListKey(), TextUtils.join(",", styles));
	}

	@Override
	public boolean fillParams(View root, MapActivity activity) {
		super.fillParams(root, activity);
		getParams().put(KEY_DIALOG, Boolean.toString(((SwitchCompat) root.findViewById(R.id.saveButton)).isChecked()));
		return true;
	}

	@Override
	protected List<String> loadListFromParams() {

		List<String> styles = new ArrayList<>();

		String filtersId = getParams().get(getListKey());

		if (filtersId != null && !filtersId.trim().isEmpty()) {
			Collections.addAll(styles, filtersId.split(","));
		}

		return styles;
	}

	@Override
	protected String getItemName(String item) {
		return item;
	}

	@Override
	protected String getTitle(List<String> filters) {

		if (filters.isEmpty()) return "";

		return filters.size() > 1
				? filters.get(0) + " +" + (filters.size() - 1)
				: filters.get(0);
	}
}