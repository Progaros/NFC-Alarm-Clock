package com.nfcalarmclock.system.scheduler

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nfcalarmclock.alarm.activealarm.NacActiveAlarmBroadcastReceiver
import com.nfcalarmclock.alarm.activealarm.NacActiveAlarmService
import com.nfcalarmclock.alarm.db.NacAlarm
import com.nfcalarmclock.alarm.options.upcomingreminder.NacUpcomingReminderService
import com.nfcalarmclock.main.NacMainActivity
import com.nfcalarmclock.util.NacCalendar
import java.text.DateFormat.getDateTimeInstance
import java.util.Calendar
import kotlin.random.Random

/**
 * The alarm scheduler.
 */
object NacScheduler
{

	/**
	 * Add all alarm days to the scheduler.
	 */
	fun add(context: Context, alarm: NacAlarm?)
	{
		// Check if the alarm is null or not enabled
		if (alarm?.isEnabled != true)
		{
			return
		}

		// Get the calendar for when the alarm should actually fire
		val alarmCal = NacCalendar.getNextAlarmDay(alarm, ignoreSkip = true)!!

		// Add the alarm to the scheduler
		addAlarm(context, alarm, alarmCal)

		// Check if should show an upcoming reminder
		if (alarm.shouldShowReminder && !alarm.shouldSkipNextAlarm)
		{
			// Get the calendar for the first upcoming reminder
			val firstReminderCal = NacCalendar.getFirstAlarmUpcomingReminder(alarm, alarmCal)

			// Add the upcoming reminder
			addUpcomingReminder(context, alarm, firstReminderCal)
		}
	}

	/**
	 * Add an alarm to the scheduler.
	 */
	private fun addAlarm(
		context: Context,
		alarm: NacAlarm,
		cal: Calendar)
	{
		// Operation to perform when the alarm goes off
		val pendingIntent = buildAddAlarmPendingIntent(context, alarm)

		// Add to the alarm manager
		addToAlarmManager(context, cal, pendingIntent)
	}

	/**
	 * Add an alarm calendar to the scheduler.
	 */
	private fun addToAlarmManager(
		context: Context,
		cal: Calendar,
		operationPendingIntent: PendingIntent)
	{
		// Show the main activity
		val showPendingIntent = buildMainActivityPendingIntent(context)

		// Create the alarm clock info and set the alarm
		val clockInfo = AlarmClockInfo(cal.timeInMillis, showPendingIntent)
		val manager = getAlarmManager(context)

		// Set the alarm
		manager.setAlarmClock(clockInfo, operationPendingIntent)
	}

	/**
	 * Add an upcoming reminder to the scheduler.
	 */
	fun addUpcomingReminder(
		context: Context,
		alarm: NacAlarm,
		reminderCal: Calendar)
	{
		// Get the current calendar and make a copy of the alarm calendar
		val now = Calendar.getInstance()

		// Check if the calendar for the upcoming reminder has already passed
		if (reminderCal.before(now))
		{
			// Do not schedule the upcoming reminder
			return
		}

		// Operation to perform when the alarm goes off
		val pendingIntent = buildAddUpcomingReminderPendingIntent(context, alarm)

		// Add to the alarm manager
		addToAlarmManager(context, reminderCal, pendingIntent)
	}

	/**
	 * Build the pending intent for adding an alarm.
	 *
	 * @return The pending intent for adding an alarm.
	 */
	@OptIn(UnstableApi::class)
	private fun buildAddAlarmPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent
	{
		// Create the intent
		val intent = if (alarm.shouldSkipNextAlarm)
		{
			NacActiveAlarmService.getSkipIntent(context, alarm)
		}
		else
		{
			NacActiveAlarmService.getStartIntent(context, alarm)
		}

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent,
			PendingIntent.FLAG_CANCEL_CURRENT)!!
	}

	/**
	 * Build the pending intent for adding an upcoming reminder.
	 *
	 * @return The pending intent for adding an upcoming reminder.
	 */
	private fun buildAddUpcomingReminderPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent
	{
		// Create the intent
		val intent = NacUpcomingReminderService.getStartIntent(context, alarm)

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_CANCEL_CURRENT)!!
	}

	/**
	 * Build the pending intent for canceling an alarm.
	 *
	 * @return The pending intent for canceling an alarm.
	 */
	@OptIn(UnstableApi::class)
	private fun buildCancelAlarmPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent?
	{
		// Create the intent
		val intent = NacActiveAlarmService.getStartIntent(context, null)

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent,
			PendingIntent.FLAG_NO_CREATE)
	}

	/**
	 * Build the pending intent for canceling a skipped alarm.
	 *
	 * @return The pending intent for canceling a skipped alarm.
	 */
	@OptIn(UnstableApi::class)
	private fun buildCancelSkipPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent?
	{
		// Create the intent
		val intent = NacActiveAlarmService.getSkipIntent(context, null)

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent,
			PendingIntent.FLAG_NO_CREATE)
	}

	/**
	 * Build the pending intent for canceling an upcoming reminder.
	 *
	 * @return The pending intent for canceling an upcoming reminder.
	 */
	private fun buildCancelUpcomingReminderPendingIntent(
		context: Context,
		alarm: NacAlarm
	): PendingIntent?
	{
		// Create the intent
		val intent = NacUpcomingReminderService.getStartIntent(context, null)

		// Build the pending intent
		return buildServicePendingIntent(context, alarm, intent, PendingIntent.FLAG_NO_CREATE)
	}

	/**
	 * Build the pending intent to launch the main activity.
	 *
	 * @return The pending intent to launch the main activity.
	 */
	private fun buildMainActivityPendingIntent(context: Context): PendingIntent?
	{
		// Get a random number to use for the ID
		val id = Random.nextInt(1, 500)

		// Get the flags
		val flags = PendingIntent.FLAG_IMMUTABLE

		// Create the intent
		val intent = Intent(context, NacMainActivity::class.java)

		// Build the pending intent
		return PendingIntent.getActivity(context, id, intent, flags)
	}

	/**
	 * Build the pending intent for an alarm.
	 *
	 * @return The pending intent for an alarm.
	 */
	private fun buildServicePendingIntent(
		context: Context,
		alarm: NacAlarm,
		intent: Intent,
		flags: Int
	): PendingIntent?
	{
		// Get the alarm ID
		val id = alarm.id.toInt()

		// Prepare the flags
		val intentFlags = flags or PendingIntent.FLAG_IMMUTABLE

		// Create the pending intent
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
		{
			PendingIntent.getForegroundService(context, id, intent, intentFlags)
		}
		else
		{
			PendingIntent.getService(context, id, intent, intentFlags)
		}
	}

	/**
	 * @see NacScheduler.cancel
	 */
	fun cancel(context: Context, alarm: NacAlarm?)
	{
		//println("NacScheduler.cancel() : $alarm")
		// Check if the alarm is null
		if (alarm == null)
		{
			return
		}

		// Cancel the alarm
		cancelAlarm(context, alarm)

		// Cancel the upcoming reminder
		cancelUpcomingReminder(context, alarm)
	}

	/**
	 * Cancel an alarm.
	 */
	private fun cancelAlarm(context: Context, alarm: NacAlarm)
	{
		// Build the pending intent for the alarm
		val alarmPendingIntent = buildCancelAlarmPendingIntent(context, alarm)
		val skipPendingIntent = buildCancelSkipPendingIntent(context, alarm)

		// Check if the alarm pending intent can be canceled
		if (alarmPendingIntent != null)
		{
			// Cancel the alarm
			getAlarmManager(context).cancel(alarmPendingIntent)
		}

		// Check if the skipped alarm pending intent can be canceled
		if (skipPendingIntent != null)
		{
			// Cancel the skipped alarm
			getAlarmManager(context).cancel(skipPendingIntent)
		}
	}

	/**
	 * Cancel the old alarm type with a given ID.
	 *
	 * @param context Context.
	 * @param id Alarm ID.
	 */
	fun cancelOld(context: Context, id: Int)
	{
		// Prepare the flags
		val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE

		// Create the pending intent for the old type
		val intent = Intent(context, NacActiveAlarmBroadcastReceiver::class.java)
		val pending = PendingIntent.getBroadcast(context, id, intent, flags)

		// Cancel the alarm
		if (pending != null)
		{
			getAlarmManager(context).cancel(pending)
		}
	}

	/**
	 * Cancel the older alarm type with a given ID.
	 *
	 * @param context Context.
	 * @param id Alarm ID.
	 */
	private fun cancelOlder(context: Context, id: Int)
	{
		// Prepare the flags
		var flags = PendingIntent.FLAG_NO_CREATE

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
		{
			flags = flags or PendingIntent.FLAG_MUTABLE
		}

		// Create the pending intent for the old type
		val intent = Intent(context, NacActiveAlarmBroadcastReceiver::class.java)
		val pending = PendingIntent.getBroadcast(context, id, intent, flags)

		// Cancel the alarm
		if (pending != null)
		{
			getAlarmManager(context).cancel(pending)
		}
	}

	/**
	 * Cancel an upcoming reminder.
	 */
	fun cancelUpcomingReminder(context: Context, alarm: NacAlarm)
	{
		// Build the pending intent for the upcoming reminder
		val pendingIntent = buildCancelUpcomingReminderPendingIntent(context, alarm)
		//println("Cancel upcoming reminder() : $pendingIntent")

		// Check if the pending intent for the upcoming reminder is not null
		if (pendingIntent != null)
		{
			// Cancel the alarm
			getAlarmManager(context).cancel(pendingIntent)
		}
	}

	/**
	 * Get the AlarmManager.
	 *
	 * @return The AlarmManager.
	 */
	private fun getAlarmManager(context: Context): AlarmManager
	{
		return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
	}

	/**
	 * Add a skipped alarm internally for skip flag clearing without reporting to Android.
	 * This uses the regular alarm manager but not setAlarmClock, so Android won't report
	 * this as the "next alarm".
	 */
	private fun addSkippedAlarmInternally(context: Context, alarm: NacAlarm)
	{
		// Get the calendar for when the alarm should fire (ignoring skip for scheduling)
		val alarmCal = NacCalendar.getNextAlarmDay(alarm, ignoreSkip = true)!!

		// Operation to perform when the alarm goes off (will clear skip flag)
		val pendingIntent = buildAddAlarmPendingIntent(context, alarm)

		// Use regular setExact instead of setAlarmClock to avoid Android reporting
		val manager = getAlarmManager(context)
		manager.setExact(AlarmManager.RTC_WAKEUP, alarmCal.timeInMillis, pendingIntent)
	}

	/**
	 * Refresh all alarms.
	 */
	fun refreshAll(context: Context, alarms: List<NacAlarm>)
	{
		// First, cancel all existing alarms
		for (a in alarms)
		{
			val id = a.id.toInt()

			// Clear out the older alarms (Do not use IMMUTABLE flag)
			cancelOlder(context, id)

			// Clear out the old alarms (Use IMMUTABLE flag)
			cancelOld(context, id)

			// Clear out any new alarms, just in case
			cancel(context, a)
		}

		// Determine which alarm should be reported to Android as the "next alarm"
		// This should be the actual next alarm that will ring (not skipped)
		val nextAlarmToReport = NacCalendar.getNextAlarm(alarms)

		// Schedule all enabled alarms, but use smart Android reporting
		for (a in alarms)
		{
			// Skip disabled alarms
			if (!a.isEnabled)
			{
				continue
			}

			// For skipped alarms that are not the "next alarm to report", 
			// we need to schedule them internally to clear the skip flag,
			// but not report them to Android as the next alarm
			if (a.shouldSkipNextAlarm && a != nextAlarmToReport)
			{
				// Schedule this alarm internally for skip flag clearing
				// but don't use setAlarmClock (which reports to Android)
				addSkippedAlarmInternally(context, a)
			}
			else
			{
				// Schedule normally (this will report to Android if it's the next alarm)
				add(context, a)
			}
		}
	}

	/**
	 * Update all days in a given alarm.
	 */
	fun update(context: Context, alarm: NacAlarm?)
	{
		// Cancel the alarm
		cancel(context, alarm)

		// Add the alarm
		add(context, alarm)
	}

	/**
	 * Update a single calendar in a given alarm.
	 */
	fun update(context: Context, alarm: NacAlarm, cal: Calendar)
	{
		// Cancel the alarm
		cancel(context, alarm)

		// Add the alarm. This will not add the reminder, but that is OK
		addAlarm(context, alarm, cal)
	}

	/**
	 * Update a list of alarms.
	 */
	fun updateAll(context: Context, alarms: List<NacAlarm>)
	{
		// Use refreshAll for consistent behavior with smart Android reporting
		refreshAll(context, alarms)
	}

}
