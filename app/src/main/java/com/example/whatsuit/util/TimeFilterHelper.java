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
import java.util.Locale;
import java.util.List;

public class TimeFilterHelper {
    // Time filter constants
    private static final int FILTER_ALL = 0;
    private static final int FILTER_TODAY = 1;
    private static final int FILTER_YESTERDAY = 2;
    private static final int FILTER_LAST_WEEK = 3;
    private static final int FILTER_LAST_MONTH = 4;
    private static final int FILTER_LAST_YEAR = 5;
    private static final int FILTER_CUSTOM = 6;

    private final Context context;
    private Dialog filterDialog;
    private RadioGroup timeFilterGroup;
    private TimeFilterCallback callback;
    private Calendar startDate;
    private Calendar endDate;
    private int currentFilter = FILTER_ALL;
    private SimpleDateFormat dateFormat;

    public interface TimeFilterCallback {
        void onTimeRangeSelected(long startTime, long endTime, String displayText);
    }

    public TimeFilterHelper(Context context, TimeFilterCallback callback) {
        this.context = context;
        this.callback = callback;
        this.startDate = Calendar.getInstance();
        this.endDate = Calendar.getInstance();
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    public void showTimeFilterDialog() {
        filterDialog = new Dialog(context);
        filterDialog.setContentView(R.layout.dialog_time_filter);

        timeFilterGroup = filterDialog.findViewById(R.id.timeFilterRadioGroup);
        View customRangeLayout = filterDialog.findViewById(R.id.customRangeLayout);
        final TextInputEditText startDateInput = filterDialog.findViewById(R.id.startDateInput);
        final TextInputEditText endDateInput = filterDialog.findViewById(R.id.endDateInput);

        // Set up filter radio group
        timeFilterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.customRangeRadio) {
                currentFilter = FILTER_CUSTOM;
                customRangeLayout.setVisibility(View.VISIBLE);
                // Initialize date inputs with current range
                updateDateInputs(startDateInput, endDateInput);
            } else {
                currentFilter = getFilterConstant(checkedId);
                customRangeLayout.setVisibility(View.GONE);
            }
        });

        // Set up date pickers for custom range
        setupDatePicker(startDateInput, true, endDateInput);
        setupDatePicker(endDateInput, false, startDateInput);

        // Set up buttons
        filterDialog.findViewById(R.id.applyButton).setOnClickListener(v -> {
            updateTimeRange();
            if (callback != null) {
                callback.onTimeRangeSelected(
                    startDate.getTimeInMillis(),
                    endDate.getTimeInMillis(),
                    getDisplayText()
                );
            }
            filterDialog.dismiss();
        });

        filterDialog.findViewById(R.id.cancelButton).setOnClickListener(v -> {
            filterDialog.dismiss();
        });

        filterDialog.show();
    }

    private void setupDatePicker(TextInputEditText dateInput, boolean isStartDate, TextInputEditText otherInput) {
        dateInput.setOnClickListener(v -> {
            Calendar calendar = isStartDate ? startDate : endDate;
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                context,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    
                    if (isStartDate) {
                        setStartOfDay(calendar);
                        if (calendar.after(endDate)) {
                            startDate.setTime(endDate.getTime());
                            setStartOfDay(startDate);
                        }
                    } else {
                        setEndOfDay(calendar);
                        if (calendar.before(startDate)) {
                            endDate.setTime(startDate.getTime());
                            setEndOfDay(endDate);
                        }
                    }
                    
                    updateDateInputs(dateInput, otherInput);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            );
            
            // Set max date to today
            datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            if (isStartDate && endDate != null) {
                datePickerDialog.getDatePicker().setMaxDate(endDate.getTimeInMillis());
            }
            
            datePickerDialog.show();
        });
    }

    private void updateTimeRange() {
        Calendar now = Calendar.getInstance();
        
        switch (currentFilter) {
            case FILTER_TODAY:
                endDate = Calendar.getInstance();
                startDate = Calendar.getInstance();
                setStartOfDay(startDate);
                setEndOfDay(endDate);
                break;
                
            case FILTER_YESTERDAY:
                endDate = Calendar.getInstance();
                startDate = Calendar.getInstance();
                endDate.add(Calendar.DAY_OF_YEAR, -1);
                startDate.add(Calendar.DAY_OF_YEAR, -1);
                setStartOfDay(startDate);
                setEndOfDay(endDate);
                break;
                
            case FILTER_LAST_WEEK:
                endDate = Calendar.getInstance();
                startDate = Calendar.getInstance();
                startDate.add(Calendar.DAY_OF_YEAR, -7);
                setStartOfDay(startDate);
                setEndOfDay(endDate);
                break;
                
            case FILTER_LAST_MONTH:
                endDate = Calendar.getInstance();
                startDate = Calendar.getInstance();
                startDate.add(Calendar.MONTH, -1);
                setStartOfDay(startDate);
                setEndOfDay(endDate);
                break;
                
            case FILTER_LAST_YEAR:
                endDate = Calendar.getInstance();
                startDate = Calendar.getInstance();
                startDate.add(Calendar.YEAR, -1);
                setStartOfDay(startDate);
                setEndOfDay(endDate);
                break;
                
            case FILTER_ALL:
                endDate = Calendar.getInstance();
                startDate = Calendar.getInstance();
                startDate.add(Calendar.YEAR, -100); // Practical "all time"
                setStartOfDay(startDate);
                setEndOfDay(endDate);
                break;
                
            case FILTER_CUSTOM:
                // Dates already set by date pickers
                break;
        }
    }

    private int getFilterConstant(int radioButtonId) {
        if (radioButtonId == R.id.allTimeRadio) return FILTER_ALL;
        if (radioButtonId == R.id.todayRadio) return FILTER_TODAY;
        if (radioButtonId == R.id.yesterdayRadio) return FILTER_YESTERDAY;
        if (radioButtonId == R.id.lastWeekRadio) return FILTER_LAST_WEEK;
        if (radioButtonId == R.id.lastMonthRadio) return FILTER_LAST_MONTH;
        if (radioButtonId == R.id.lastYearRadio) return FILTER_LAST_YEAR;
        if (radioButtonId == R.id.customRangeRadio) return FILTER_CUSTOM;
        return FILTER_ALL;
    }

    private void updateDateInputs(TextInputEditText dateInput, TextInputEditText otherInput) {
        dateInput.setText(dateFormat.format(startDate.getTime()));
        otherInput.setText(dateFormat.format(endDate.getTime()));
    }

    private void setStartOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void setEndOfDay(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
    }

    private String getDisplayText() {
        switch (currentFilter) {
            case FILTER_ALL:
                return "All Time";
            case FILTER_TODAY:
                return "Today";
            case FILTER_YESTERDAY:
                return "Yesterday";
            case FILTER_LAST_WEEK:
                return "Last 7 Days";
            case FILTER_LAST_MONTH:
                return "Last 30 Days";
            case FILTER_LAST_YEAR:
                return "Last Year";
            case FILTER_CUSTOM:
                return dateFormat.format(startDate.getTime()) + " - " + 
                       dateFormat.format(endDate.getTime());
            default:
                return "All Time";
        }
    }

    public boolean isWithinTimeFilter(long timestamp) {
        return currentFilter == FILTER_ALL || 
               (timestamp >= startDate.getTimeInMillis() && 
                timestamp <= endDate.getTimeInMillis());
    }

    public <T> List<T> filterByTime(List<T> items, TimestampExtractor<T> timestampExtractor) {
        if (currentFilter == FILTER_ALL || items == null || items.isEmpty()) {
            return items;
        }

        long startTime = startDate.getTimeInMillis();
        long endTime = endDate.getTimeInMillis();

        return items.stream()
                   .filter(item -> {
                       long timestamp = timestampExtractor.getTimestamp(item);
                       return timestamp >= startTime && timestamp <= endTime;
                   })
                   .collect(java.util.stream.Collectors.toList());
    }

    public interface TimestampExtractor<T> {
        long getTimestamp(T item);
    }

    public void dismiss() {
        if (filterDialog != null && filterDialog.isShowing()) {
            filterDialog.dismiss();
        }
    }

    public long getStartTimeMillis() {
        return startDate.getTimeInMillis();
    }

    public long getEndTimeMillis() {
        return endDate.getTimeInMillis();
    }
}
