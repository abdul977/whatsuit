package com.example.whatsuit.util;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.example.whatsuit.R;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class TimeFilterHelper {
    private final Context context;
    private Dialog filterDialog;
    private RadioGroup timeFilterGroup;
    private TimeFilterCallback callback;

    public interface TimeFilterCallback {
        void onTimeRangeSelected(long startTime, long endTime);
    }

    public TimeFilterHelper(Context context, TimeFilterCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void showTimeFilterDialog() {
        filterDialog = new Dialog(context);
        filterDialog.setContentView(R.layout.dialog_time_filter);

        timeFilterGroup = filterDialog.findViewById(R.id.timeFilterRadioGroup);
        View customRangeLayout = filterDialog.findViewById(R.id.customRangeLayout);
        final TextInputEditText startDateInput = filterDialog.findViewById(R.id.startDateInput);
        final TextInputEditText endDateInput = filterDialog.findViewById(R.id.endDateInput);

        filterDialog.findViewById(R.id.applyButton).setOnClickListener(v -> {
            applyTimeFilter(timeFilterGroup.getCheckedRadioButtonId());
        });

        filterDialog.findViewById(R.id.cancelButton).setOnClickListener(v -> {
            filterDialog.dismiss();
        });

        timeFilterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.customRangeRadio) {
                customRangeLayout.setVisibility(View.VISIBLE);
            } else {
                customRangeLayout.setVisibility(View.GONE);
                applyTimeFilter(checkedId);
            }
        });

        startDateInput.setOnClickListener(v -> showDatePicker(startDateInput));
        endDateInput.setOnClickListener(v -> showDatePicker(endDateInput));

        filterDialog.show();
    }

    private void showDatePicker(final TextInputEditText dateInput) {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(context,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    calendar.set(selectedYear, selectedMonth, selectedDay);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    dateInput.setText(dateFormat.format(calendar.getTime()));
                }, year, month, day);

        datePickerDialog.show();
    }

    private void applyTimeFilter(int filterId) {
        Calendar calendar = Calendar.getInstance();
        long endTime = calendar.getTimeInMillis();
        long startTime;

        RadioButton selectedButton = filterDialog.findViewById(filterId);
        String filterText = selectedButton.getText().toString();

        switch (filterText) {
            case "Today":
                setStartOfDay(calendar);
                startTime = calendar.getTimeInMillis();
                endTime = System.currentTimeMillis();
                break;

            case "Yesterday":
                setStartOfDay(calendar);
                calendar.add(Calendar.DAY_OF_YEAR, -1);
                startTime = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                endTime = calendar.getTimeInMillis();
                break;

            case "Last Week":
                calendar.add(Calendar.WEEK_OF_YEAR, -1);
                startTime = calendar.getTimeInMillis();
                endTime = System.currentTimeMillis();
                break;

            case "Last Month":
                calendar.add(Calendar.MONTH, -1);
                startTime = calendar.getTimeInMillis();
                endTime = System.currentTimeMillis();
                break;

            case "Last Year":
                calendar.add(Calendar.YEAR, -1);
                startTime = calendar.getTimeInMillis();
                endTime = System.currentTimeMillis();
                break;

            case "All Time":
            default:
                calendar.add(Calendar.YEAR, -100);
                startTime = calendar.getTimeInMillis();
                endTime = System.currentTimeMillis();
                break;
        }

        if (callback != null) {
            callback.onTimeRangeSelected(startTime, endTime);
        }

        if (filterDialog != null && filterDialog.isShowing()) {
            filterDialog.dismiss();
        }
    }

    private void setStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    public void dismiss() {
        if (filterDialog != null && filterDialog.isShowing()) {
            filterDialog.dismiss();
        }
    }
}