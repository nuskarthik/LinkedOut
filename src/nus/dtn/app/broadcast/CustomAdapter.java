package nus.dtn.app.broadcast;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class CustomAdapter extends ArrayAdapter<ListModel> {
  private final Context context;
  private final ArrayList<ListModel> values;

  public CustomAdapter(Context context, ArrayList<ListModel> values) {
    super(context, R.layout.tabitem, values);
    this.context = context;
    this.values = values;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    LayoutInflater inflater = (LayoutInflater) context
        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View rowView = inflater.inflate(R.layout.tabitem, parent, false);
    TextView nameView = (TextView) rowView.findViewById(R.id.NameText);
    TextView locationView = (TextView) rowView.findViewById(R.id.LocationText);
    TextView availabilityView = (TextView) rowView.findViewById(R.id.AvailabilityText);
    nameView.setText(values.get(position).getName());
    locationView.setText(values.get(position).getLocation());
    availabilityView.setText(values.get(position).getAvailability());
    // change the icon for Windows and iPhone
//    String s = values[position];
//    if (s.startsWith("iPhone")) {
//      imageView.setImageResource(R.drawable.no);
//    } else {
//      imageView.setImageResource(R.drawable.ok);
//    }

    return rowView;
  }
}