package com.guardian.hadoop.integration.cm;

import java.util.ArrayList;
import java.util.List;

public class ApiAlertsResponse {

    private List<ApiAlertItem> items = new ArrayList<ApiAlertItem>();

    public List<ApiAlertItem> getItems() {
        return items;
    }

    public void setItems(List<ApiAlertItem> items) {
        this.items = items;
    }
}
