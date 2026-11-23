package com.abgl.dms.navigation;

import java.util.List;

public record NavigationItem(String id, String label, String icon, List<String> subMenus) {}
