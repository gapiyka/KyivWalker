package com.gapiyka.kyivwalker

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import android.widget.Toast


class HomeScreenWidgetProvider : AppWidgetProvider() {
    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        //Toast.makeText(context, "Delete", Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        //Toast.makeText(context, "Disable", Toast.LENGTH_LONG).show()
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        //Toast.makeText(context, "Enable", Toast.LENGTH_LONG).show()
    }

    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray?
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.statsFragment)
    }
}
