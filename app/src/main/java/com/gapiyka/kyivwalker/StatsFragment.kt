package com.gapiyka.kyivwalker

import android.content.Context
import android.opengl.Visibility
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class StatsFragment : Fragment() {
    private lateinit var tSpeed : TextView
    private lateinit var tMaxSpeed : TextView
    private lateinit var view : View

    fun SwitchVisibility(){
        if(view.visibility == View.INVISIBLE)
            view.visibility = View.VISIBLE
        else
            view.visibility = View.INVISIBLE
    }

    fun SetText(speed : Float, max : Float){
        tSpeed.text = speed.toString()
        tMaxSpeed.text = max.toString()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        view = inflater.inflate(R.layout.fragment_stats, container, false)
        tSpeed = view.findViewById<TextView>(R.id.textSpeed)
        tMaxSpeed = view.findViewById<TextView>(R.id.textMaxSpeed)
        SwitchVisibility()
        return view
    }
}