# Copy review (issue #29)

German uses **du**. Review of every user-facing string on `main` at 2026-11-05. Strings added later (M3 screens) follow the term table. Tone rules: `.claude/rules/copy-and-tone.md`, ADR-0012, ADR-0014.

## Terms (decided by the owner, 2026-11-02)

| English | German |
|---|---|
| waiting period / cooling off | Wartezeit |
| wishlist | Wunschliste |
| priorities | Prioritäten |
| make it a priority | Zur Priorität machen |
| fun money | Spaßgeld |
| set aside | zurücklegen / zurückgelegt |
| not yet assigned (#88) | Noch nicht verteilt |
| decided against | Dagegen entschieden |
| let it go | Loslassen |
| third-party licences (#98) | Lizenzen von Drittanbietern |

English UI text says "waiting period", never "cooling off" (code and docs may still say cooling-off).

## Result

- Scan for shaming, urgency (only, hurry, last chance), celebration (saved, streak, well done), "afford", "available", deals and prices: **no violations** in any key. "Watch" flags below are borderline wordings that were checked and kept.
- Changed: `stage_cooling_off`, `countdown_over`, `decision_cooling_done` (EN, term table); `stage_saving` (DE, "Sparen" → "Zurücklegen").
- Left to #88: `home_money_unallocated` and the three keys that reuse "not yet set aside".
- Plurals: every plural has `one` and `other` in both languages. German `notification_nudge_digest_title` and `home_considering_count` are identical in both forms by design.
- Long words: the German wishlist, home and decision screens are screenshot-tested at font scale 1.3 (`*ScreenshotDeLargeFontTest`); text wraps, nothing clips.

## Every string

| Key | English | Deutsch | Flag |
|---|---|---|---|
| `app_name` | Before You Buy | Before You Buy |  |
| `home_decide_title` | To decide | Zu entscheiden |  |
| `home_considering_title` | Considering | In Überlegung |  |
| `home_considering_empty` | Nothing yet. Add something you’d like to buy. It waits on your wishlist while you think it over. | Noch nichts. Füge etwas hinzu, das du gern kaufen würdest. Es wartet auf deiner Wunschliste, während du darüber nachdenkst. |  |
| `home_next_cooling_end` | Next waiting period to end | Als Nächstes endet die Wartezeit für |  |
| `home_money_title` | Fun money | Spaßgeld |  |
| `home_money_not_set_up` | Choose a monthly amount for the things you want. It’s added each month by itself. | Wähle einen monatlichen Betrag für Dinge, die du dir wünschst. Er kommt jeden Monat von selbst dazu. |  |
| `home_money_set_up` | Set up | Einrichten |  |
| `home_money_manage` | Change or adjust | Ändern oder anpassen |  |
| `home_money_pool` | Fun money for wants | Spaßgeld für Wünsche |  |
| `home_money_committed` | Set aside for wants | Für Wünsche zurückgelegt |  |
| `home_money_unallocated` | Not yet set aside | Noch nicht zurückgelegt | #88 renames this key |
| `fun_money_title` | Fun money | Spaßgeld |  |
| `fun_money_explained` | A monthly amount for the things you want, added by itself on the day you choose. It’s only a number in this app; no real money moves. | Ein monatlicher Betrag für Dinge, die du dir wünschst. Er kommt an dem Tag, den du wählst, von selbst dazu. Er ist nur eine Zahl in dieser App; echtes Geld bewegt sich nicht. |  |
| `fun_money_balance_title` | Now | Aktuell |  |
| `fun_money_next_accrual` | Next monthly amount on %1$s | Nächster Monatsbetrag am %1$s |  |
| `fun_money_monthly_title` | Each month | Jeden Monat |  |
| `fun_money_monthly` | Monthly amount | Monatlicher Betrag |  |
| `fun_money_amount_missing` | Enter an amount | Gib einen Betrag ein |  |
| `fun_money_accrual_day` | Added on day of the month | Kommt dazu am Tag des Monats |  |
| `fun_money_first_accrual` | First added on %1$s | Zum ersten Mal am %1$s |  |
| `fun_money_starting_balance` | Starting amount (optional) | Startbetrag (optional) |  |
| `fun_money_starting_balance_explained` | What you had for wants when you started, before the first monthly amount. | Was du zu Beginn für Wünsche hattest, vor dem ersten Monatsbetrag. |  |
| `fun_money_changes_apply` | A new monthly amount or day applies from the next monthly amount on. | Ein neuer Monatsbetrag oder Tag gilt ab dem nächsten Monatsbetrag. |  |
| `fun_money_adjust_title` | Adjust the amount | Betrag anpassen |  |
| `fun_money_adjust_explained` | If the number here and your real money drift apart, correct it. | Wenn die Zahl hier und dein echtes Geld auseinanderlaufen, korrigiere sie. |  |
| `fun_money_adjust_amount` | Amount | Betrag |  |
| `fun_money_adjust_note` | Note (optional) | Notiz (optional) |  |
| `fun_money_add` | Add | Hinzufügen |  |
| `fun_money_take_out` | Take out | Herausnehmen |  |
| `fun_money_below_zero_title` | Go below zero? | Unter null gehen? |  |
| `fun_money_below_zero_body` | Taking out %1$s leaves %2$s not yet set aside. | Wenn du %1$s herausnimmst, bleiben %2$s noch nicht zurückgelegt. | #88 renames this key |
| `countdown_over` | Waiting period over | Wartezeit vorbei | Fixed: EN now "Waiting period over" (was "Cooling-off over") |
| `wishlist_title` | Wishlist | Wunschliste |  |
| `wishlist_open` | Open wishlist | Wunschliste öffnen |  |
| `wishlist_empty_title` | Your wishlist is empty | Deine Wunschliste ist leer |  |
| `wishlist_empty_body` | Add something you want. It waits here while you think it over. | Füge etwas hinzu, das du gern hättest. Es wartet hier, während du darüber nachdenkst. |  |
| `wishlist_ready_to_decide` | Ready to decide | Bereit zur Entscheidung |  |
| `stage_cooling_off` | Waiting period | Wartezeit | Fixed: EN now "Waiting period" (was "Cooling off") |
| `stage_needs_reflection` | Needs reflection | Zum Überdenken |  |
| `stage_saving` | Saving | Zurücklegen | Fixed: DE now "Zurücklegen" (was "Sparen") |
| `stage_not_priority` | Not one of your priorities | Keine deiner Prioritäten |  |
| `stage_ready` | Ready | Bereit |  |
| `wishlist_needs` | Needs | Notwendiges |  |
| `priorities_empty_hint` | Make a want a priority to start its waiting period. | Mach einen Wunsch zur Priorität, damit seine Wartezeit beginnt. |  |
| `wait_progress_done` | Waiting period done | Wartezeit vorbei |  |
| `relative_today` | Today | Heute |  |
| `relative_yesterday` | Yesterday | Gestern |  |
| `quick_add_open` | Add | Hinzufügen |  |
| `quick_add_title` | Quick add | Schnell hinzufügen |  |
| `quick_add_save` | Save | Speichern |  |
| `quick_add_name` | Name | Name |  |
| `quick_add_name_missing` | Enter a name | Gib einen Namen ein |  |
| `quick_add_price` | Price | Preis |  |
| `quick_add_price_missing` | Enter a price | Gib einen Preis ein |  |
| `quick_add_price_invalid` | Enter an amount like %1$s | Gib einen Betrag wie %1$s ein |  |
| `quick_add_price_invalid_no_example` | Enter an amount | Gib einen Betrag ein |  |
| `quick_add_price_whole_only` | Enter a whole amount, without decimals | Gib einen ganzen Betrag ohne Nachkommastellen ein |  |
| `quick_add_price_too_large` | That amount is too large | Dieser Betrag ist zu groß |  |
| `quick_add_link` | Link (optional) | Link (optional) |  |
| `quick_add_need` | This is a need | Das ist notwendig |  |
| `quick_add_need_explained` | Needs skip the waiting period | Notwendiges hat keine Wartezeit |  |
| `quick_add_more_details` | More details | Mehr Details |  |
| `quick_add_fewer_details` | Fewer details | Weniger Details |  |
| `quick_add_category` | Category (optional) | Kategorie (optional) |  |
| `quick_add_notes` | Notes (optional) | Notizen (optional) |  |
| `quick_add_target_date_none` | Target date (optional) | Zieldatum (optional) |  |
| `quick_add_target_date` | Target date: %1$s | Zieldatum: %1$s |  |
| `quick_add_target_date_remove` | Remove date | Datum entfernen |  |
| `quick_add_saved_need` | Added as a need. | Als notwendig hinzugefügt. |  |
| `quick_add_saved_want` | Added to your wishlist. | Zu deiner Wunschliste hinzugefügt. |  |
| `share_fallback_name` | %1$s item | Artikel von %1$s |  |
| `dialog_ok` | OK | OK |  |
| `dialog_cancel` | Cancel | Abbrechen |  |
| `item_edit` | Edit | Bearbeiten |  |
| `item_more_options` | More options | Weitere Optionen |  |
| `item_copy_link` | Copy link | Link kopieren |  |
| `item_open_link` | Open link | Link öffnen |  |
| `item_link_copied` | Link copied | Link kopiert |  |
| `item_link_not_opened` | No app can open this link | Keine App kann diesen Link öffnen |  |
| `item_added_today` | Added today | Heute hinzugefügt |  |
| `item_need` | A need, so no waiting period | Notwendig, daher ohne Wartezeit |  |
| `item_reasons_title` | Your reasons | Deine Gründe |  |
| `item_reason_why` | Why you want it | Warum du es willst |  |
| `item_reason_usage` | How often you’ll use it | Wie oft du es nutzen wirst |  |
| `item_reasons_against` | Reasons against | Gründe dagegen |  |
| `item_reason_problem_solved` | What problem it solves | Welches Problem es löst |  |
| `item_reason_current_alternative` | What you use now instead | Was du stattdessen jetzt nutzt |  |
| `item_reason_replaces` | What it replaces | Was es ersetzt |  |
| `item_reason_why_now` | Why now | Warum jetzt |  |
| `item_reason_upgrade_or_new` | Upgrade or new | Upgrade oder neu |  |
| `item_reflect_prompt_title` | Why do you want this? | Warum willst du das? |  |
| `item_reflect_prompt_title_started` | A few answers are still missing | Ein paar Antworten fehlen noch |  |
| `item_reflect_prompt_body` | Take a minute to note your reasons, for and against. You’ll see them again when you decide. | Nimm dir eine Minute für deine Gründe, dafür und dagegen. Du siehst sie wieder, wenn du entscheidest. |  |
| `item_reflect_start` | Start | Beginnen |  |
| `item_reflect_continue` | Continue | Fortsetzen |  |
| `item_reasons_edit` | Edit answers | Antworten bearbeiten |  |
| `item_reasons_add` | Add your reasons | Deine Gründe hinzufügen |  |
| `item_details_title` | Details | Details |  |
| `item_category` | Category | Kategorie |  |
| `item_notes` | Notes | Notizen |  |
| `item_target_date` | Target date | Zieldatum |  |
| `item_mark_as_need` | Mark as need | Als notwendig markieren |  |
| `item_mark_as_need_title` | Mark as a need? | Als notwendig markieren? |  |
| `item_mark_as_need_body` | Needs skip the waiting period and reflection. History will show it as a need. | Notwendiges hat keine Wartezeit und braucht keine Reflexion. Im Verlauf steht es als notwendig. |  |
| `item_changed_meanwhile` | This item changed in the meantime. Please try again. | Der Artikel hat sich inzwischen geändert. Versuch es bitte noch einmal. |  |
| `item_gone` | This item is no longer on your wishlist | Dieser Artikel ist nicht mehr auf deiner Wunschliste |  |
| `usage_daily` | Daily | Täglich |  |
| `usage_weekly` | Weekly | Wöchentlich |  |
| `usage_monthly` | Monthly | Monatlich |  |
| `usage_rarely` | Rarely | Selten |  |
| `usage_once` | Once | Einmal |  |
| `upgrade_or_new_upgrade` | An upgrade | Ein Upgrade |  |
| `upgrade_or_new_new` | Genuinely new | Wirklich neu |  |
| `reflection_close` | Close | Schließen |  |
| `reflection_progress` | Question %1$d of %2$d | Frage %1$d von %2$d |  |
| `reflection_required` | Required | Erforderlich |  |
| `reflection_optional` | Optional | Optional |  |
| `reflection_answer` | Your answer | Deine Antwort |  |
| `reflection_why_title` | Why do you want this? | Warum willst du das? |  |
| `reflection_why_help` | A few words are enough. You’ll see them again when you decide. | Ein paar Worte genügen. Du siehst sie wieder, wenn du entscheidest. |  |
| `reflection_usage_title` | How often will you use it? | Wie oft wirst du es nutzen? |  |
| `reflection_usage_help` | Your best guess is fine. | Deine beste Schätzung genügt. |  |
| `reflection_reasons_against_title` | What speaks against buying it? | Was spricht dagegen, es zu kaufen? |  |
| `reflection_reasons_against_help` | For example: price, space, I already have something similar. Add as many as you like. | Zum Beispiel: Preis, Platz, ich habe schon etwas Ähnliches. Füge so viele hinzu, wie du magst. |  |
| `reflection_reason` | Reason %1$d | Grund %1$d |  |
| `reflection_remove_reason` | Remove reason %1$d | Grund %1$d entfernen |  |
| `reflection_add_reason` | Add another reason | Weiteren Grund hinzufügen |  |
| `reflection_problem_solved_title` | What problem does it solve? | Welches Problem löst es? |  |
| `reflection_problem_solved_help` | If there is one. | Falls es eins gibt. |  |
| `reflection_current_alternative_title` | What do you use now instead? | Was nutzt du jetzt stattdessen? |  |
| `reflection_current_alternative_help` | Something you have, borrow or do differently, if anything. | Etwas, das du hast, ausleihst oder anders machst, falls überhaupt. |  |
| `reflection_replaces_title` | What will it replace? | Was wird es ersetzen? |  |
| `reflection_replaces_help` | Something it would take the place of, if anything. | Etwas, an dessen Stelle es treten würde, falls überhaupt. |  |
| `reflection_why_now_title` | Why now? | Warum jetzt? |  |
| `reflection_why_now_help` | Is there something that makes it now rather than later? | Gibt es etwas, weshalb jetzt und nicht später? |  |
| `reflection_upgrade_or_new_title` | Upgrade or genuinely new? | Upgrade oder wirklich neu? |  |
| `reflection_upgrade_or_new_help` | Does it improve on something you have, or is it new to you? | Verbessert es etwas, das du hast, oder ist es neu für dich? |  |
| `reflection_back` | Back | Zurück |  |
| `reflection_next` | Next | Weiter |  |
| `reflection_skip` | Skip | Überspringen |  |
| `reflection_done` | Done | Fertig |  |
| `item_edit_wait_extended` | Waiting period extended to %1$s | Wartezeit verlängert bis %1$s |  |
| `item_edit_conflict` | This item changed in the meantime, so nothing was saved. Check your changes and save again. | Der Artikel hat sich inzwischen geändert, daher wurde nichts gespeichert. Prüf deine Änderungen und speichere noch einmal. |  |
| `item_photo` | Photo | Foto |  |
| `item_photo_tap_to_view` | Tap to view | Zum Ansehen tippen |  |
| `item_photo_description` | Photo of %1$s | Foto von %1$s |  |
| `item_edit_photo_add` | Add photo | Foto hinzufügen |  |
| `item_edit_photo_change` | Change photo | Foto ändern |  |
| `item_edit_photo_remove` | Remove photo | Foto entfernen |  |
| `item_edit_photo_failed` | This photo couldn’t be added. Try another one. | Dieses Foto konnte nicht hinzugefügt werden. Versuch ein anderes. |  |
| `wishlist_waiting` | Looking again later | Später noch mal ansehen |  |
| `wishlist_look_again_on` | Look again on %1$s | Wieder ansehen am %1$s |  |
| `item_decide` | Decide | Entscheiden |  |
| `item_decide_now` | Decide now | Jetzt entscheiden |  |
| `decision_title` | Decide | Entscheiden |  |
| `decision_back` | Back | Zurück |  |
| `decision_added_today` | You added this today | Heute hinzugefügt |  |
| `decision_cooling_done` | Waiting period completed | Wartezeit abgeschlossen | Fixed: EN now "Waiting period completed" |
| `decision_recheck_on` | You wanted to look again on %1$s | Du wolltest es am %1$s wieder ansehen |  |
| `decision_no_reasons` | No reasons noted yet. | Noch keine Gründe notiert. |  |
| `decision_answer_questions` | Answer the questions | Fragen beantworten |  |
| `decision_question` | Do you still want to spend this money on this? | Willst du dieses Geld immer noch dafür ausgeben? | Watch: "spend" in a neutral question; kept (decision wording, no pressure) |
| `decision_buy` | Buy | Kaufen |  |
| `decision_reject` | Don’t buy — keep %1$s | Nicht kaufen – %1$s behalten |  |
| `decision_reject_for` | Don’t buy — keep %1$s for your %2$s (#%3$d) | Nicht kaufen – %1$s für %2$s (#%3$d) behalten |  |
| `decision_wait` | Wait | Warten |  |
| `decision_early` | Decide early… | Vorzeitig entscheiden … |  |
| `decision_buy_title` | Record the purchase | Kauf festhalten |  |
| `decision_price_paid` | Price paid | Bezahlter Preis |  |
| `decision_bought_on` | Bought on %1$s | Gekauft am %1$s |  |
| `decision_buy_confirm` | Buy | Kaufen |  |
| `decision_early_title` | Buy before it’s ready? | Kaufen, bevor es bereit ist? |  |
| `decision_early_wait_over` | The waiting period is over, but your reasons aren’t complete yet. | Die Wartezeit ist vorbei, aber deine Gründe sind noch nicht vollständig. |  |
| `decision_early_reasons_against` | Your reasons against | Deine Gründe dagegen |  |
| `decision_early_no_reasons_against` | You haven’t noted any reasons against yet. | Du hast noch keine Gründe dagegen notiert. |  |
| `decision_early_reason` | Why buy it now? | Warum jetzt kaufen? |  |
| `decision_early_reason_help` | Needed to buy early. It’s kept with the purchase. | Nötig für einen vorzeitigen Kauf. Er wird mit dem Kauf gespeichert. |  |
| `decision_early_confirm` | Buy anyway | Trotzdem kaufen |  |
| `decision_wait_title` | When do you want to look again? | Wann willst du es dir wieder ansehen? |  |
| `decision_wait_explained` | Until then, it isn’t shown as ready. | Bis dahin wird es nicht als bereit angezeigt. |  |
| `decision_wait_pick` | Pick a date | Datum wählen |  |
| `decision_wait_on` | On %1$s | Am %1$s |  |
| `decision_wait_confirm` | Wait until %1$s | Warten bis %1$s |  |
| `decision_reject_title` | Don’t buy it | Nicht kaufen |  |
| `decision_reject_explained` | It moves to “Decided against”. | Es kommt zu „Dagegen entschieden“. |  |
| `decision_reject_note` | Why not? (optional) | Warum nicht? (optional) |  |
| `decision_reject_confirm` | Don’t buy | Nicht kaufen |  |
| `decision_rejected` | Got it. It’s in “Decided against”. | Alles klar. Es ist jetzt unter „Dagegen entschieden“. |  |
| `decision_bought` | Got it. It’s in “Bought”. | Alles klar. Es ist jetzt unter „Gekauft“. |  |
| `history_open` | History | Verlauf |  |
| `history_title` | History | Verlauf |  |
| `history_tab_bought` | Bought | Gekauft |  |
| `history_tab_decided_against` | Decided against | Dagegen entschieden |  |
| `history_period_all` | All | Alle |  |
| `history_period_this_month` | This month | Dieser Monat |  |
| `history_period_this_year` | This year | Dieses Jahr |  |
| `history_bought_empty` | Nothing bought yet | Noch nichts gekauft |  |
| `history_bought_empty_month` | Nothing bought this month | Diesen Monat nichts gekauft |  |
| `history_bought_empty_year` | Nothing bought this year | Dieses Jahr nichts gekauft |  |
| `history_bought_empty_body` | What you buy from your wishlist is listed here. | Was du von deiner Wunschliste kaufst, steht hier. |  |
| `history_decided_against_empty` | Nothing decided against yet | Noch nichts dagegen entschieden |  |
| `history_decided_against_empty_month` | Nothing decided against this month | Diesen Monat nichts dagegen entschieden |  |
| `history_decided_against_empty_year` | Nothing decided against this year | Dieses Jahr nichts dagegen entschieden |  |
| `history_decided_against_empty_body` | What you decide not to buy is listed here. | Wogegen du dich entscheidest, steht hier. |  |
| `history_decided_against_total` | You decided against %1$s of purchases | Du hast dich gegen Käufe über %1$s entschieden | Watch: a total of not-bought items; plain statement per ADR-0012, no "saved" |
| `history_decided_against_total_month` | This month you decided against %1$s of purchases | Diesen Monat hast du dich gegen Käufe über %1$s entschieden | Watch: as above |
| `history_decided_against_total_year` | This year you decided against %1$s of purchases | Dieses Jahr hast du dich gegen Käufe über %1$s entschieden | Watch: as above |
| `history_bought_on` | Bought on %1$s | Gekauft am %1$s |  |
| `history_decided_on` | Decided on %1$s | Entschieden am %1$s |  |
| `history_wishlist_price` | Wishlist price: %1$s | Preis auf der Wunschliste: %1$s |  |
| `history_considered_same_day` | Bought on the day it was added | Am selben Tag gekauft, an dem es dazukam |  |
| `history_flag_early` | Bought early | Vorzeitig gekauft |  |
| `history_flag_need` | Need | Notwendig |  |
| `history_item_gone` | This entry is no longer in your history | Dieser Eintrag ist nicht mehr in deinem Verlauf |  |
| `history_early_reason` | Why it was bought early | Warum vorzeitig gekauft |  |
| `history_note` | Why not | Warum nicht |  |
| `history_reasons_title` | Your reasons at the time | Deine Gründe damals |  |
| `history_want_again` | Want it again? | Wieder haben wollen? |  |
| `history_want_again_title` | Put it back on your wishlist? | Wieder auf die Wunschliste? |  |
| `history_want_again_body` | It’s added as a new item and waits again from today. This entry stays in your history. | Es kommt als neuer Eintrag dazu und wartet ab heute wieder. Dieser Eintrag bleibt in deinem Verlauf. |  |
| `history_want_again_confirm` | Add to wishlist | Zur Wunschliste |  |
| `history_delete` | Delete from history | Aus dem Verlauf löschen |  |
| `history_delete_title` | Delete from history? | Aus dem Verlauf löschen? |  |
| `history_delete_body` | “%1$s” and your answers about it are deleted for good. This can’t be undone. | „%1$s“ und deine Antworten dazu werden endgültig gelöscht. Das lässt sich nicht rückgängig machen. |  |
| `history_delete_confirm` | Delete | Löschen |  |
| `notification_channel_waiting_periods` | Waiting periods | Wartezeiten |  |
| `notification_channel_waiting_periods_description` | When something you want has finished its waiting period | Wenn die Wartezeit für etwas, das du willst, vorbei ist |  |
| `notification_channel_reflection` | Reflection reminders | Erinnerungen zum Nachdenken |  |
| `notification_channel_reflection_description` | One reminder per item to note why you want it | Eine Erinnerung pro Artikel, zu notieren, warum du ihn willst |  |
| `notification_still_want_it` | Still want it | Will ich noch |  |
| `notification_let_it_go` | Let it go | Loslassen | Term table: Loslassen (ok) |
| `notification_nudge_text` | Take a minute to note why you want %1$s. | Nimm dir eine Minute und notiere, warum du %1$s willst. |  |
| `notification_nudge_digest_text` | Note why you want %1$s. | Notiere, warum du %1$s willst. |  |
| `notification_permission_text` | Get one calm notification when a waiting period ends, at most once a day. | Erhalte eine ruhige Benachrichtigung, wenn eine Wartezeit endet – höchstens einmal am Tag. | Watch: notification, at most once a day; no urgency words |
| `notification_permission_allow` | Allow notifications | Benachrichtigungen erlauben |  |
| `notification_permission_not_now` | Not now | Jetzt nicht |  |
| `fun_money_wants_need_funding` | Wants need their price set aside | Wünsche brauchen ihren Preis zurückgelegt |  |
| `fun_money_wants_need_funding_explained` | A want is ready to decide on once its price is set aside. Off: the waiting period and your reasons are enough. | Ein Wunsch ist bereit zur Entscheidung, sobald sein Preis zurückgelegt ist. Aus: Die Wartezeit und deine Gründe reichen. |  |
| `savings_title` | Set aside | Zurückgelegt |  |
| `savings_progress` | %1$s of %2$s set aside · %3$s to go | %1$s von %2$s zurückgelegt · noch %3$s |  |
| `savings_progress_complete` | %1$s of %2$s set aside | %1$s von %2$s zurückgelegt |  |
| `savings_to_go` | %1$s to go | Noch %1$s |  |
| `savings_not_set_up` | Set money aside for this from your fun money. | Leg dafür Geld aus deinem Spaßgeld zurück. |  |
| `savings_set_up` | Set up fun money | Spaßgeld einrichten |  |
| `savings_once_priority` | You can set money aside once it’s a priority. | Geld zurücklegen kannst du, sobald der Wunsch eine Priorität ist. |  |
| `savings_set_aside` | Set aside | Zurücklegen |  |
| `savings_take_back` | Take back | Zurücknehmen |  |
| `savings_sheet_set_aside_title` | Set aside for %1$s | Für %1$s zurücklegen |  |
| `savings_sheet_take_back_title` | Take back from %1$s | Von %1$s zurücknehmen |  |
| `savings_amount` | Amount | Betrag |  |
| `savings_not_set_aside` | Not yet set aside: %1$s | Noch nicht zurückgelegt: %1$s | #88 renames this key |
| `savings_held` | Set aside for it: %1$s | Dafür zurückgelegt: %1$s |  |
| `savings_chip_rest` | The rest (%1$s) | Den Rest (%1$s) |  |
| `savings_chip_all` | All (%1$s) | Alles (%1$s) |  |
| `savings_set_aside_up_to` | You can set aside up to %1$s here. | Hier kannst du bis zu %1$s zurücklegen. |  |
| `savings_take_back_up_to` | You can take back up to %1$s. | Du kannst bis zu %1$s zurücknehmen. |  |
| `savings_amount_zero` | Enter an amount above zero. | Gib einen Betrag über null ein. |  |
| `savings_changed_meanwhile` | Something changed meanwhile. Nothing was set aside or taken back. | Inzwischen hat sich etwas geändert. Es wurde nichts zurückgelegt oder zurückgenommen. |  |
| `item_mark_as_need_release` | The %1$s set aside for it goes back to your fun money. | Die dafür zurückgelegten %1$s gehen zurück in dein Spaßgeld. |  |
| `decision_early_saving` | The waiting period is over, but %1$s of the price isn’t set aside yet. | Die Wartezeit ist vorbei, aber vom Preis sind %1$s noch nicht zurückgelegt. |  |
| `decision_below_zero_body` | Paying %1$s leaves %2$s not yet set aside. | Wenn du %1$s bezahlst, bleiben %2$s noch nicht zurückgelegt. | #88 renames this key |
| `priorities_title` | Priorities | Prioritäten |  |
| `priority_rank` | #%1$d | — |  |
| `priority_item_rank` | Priority #%1$d | Priorität #%1$d |  |
| `priority_make` | Make it a priority | Zur Priorität machen |  |
| `priority_remove` | Remove from priorities | Aus den Prioritäten nehmen |  |
| `priority_promoted` | %1$s is now priority #%2$d. | %1$s ist jetzt Priorität #%2$d. |  |
| `priority_removed` | %1$s is no longer a priority. | %1$s ist keine Priorität mehr. |  |
| `priority_changed_meanwhile` | Your priorities changed meanwhile. Nothing was changed. | Deine Prioritäten haben sich inzwischen geändert. Es wurde nichts geändert. |  |
| `priority_row_options` | More options for %1$s | Weitere Optionen für %1$s |  |
| `priority_slots_change` | Change | Ändern |  |
| `priority_slots_title` | Priority slots | Prioritätsplätze |  |
| `priority_slots_body` | How many wants can be priorities at once? | Wie viele Wünsche können gleichzeitig Prioritäten sein? |  |
| `item_mark_as_need_leaves_priorities` | It also leaves your priorities, because a need can’t be one. | Der Artikel ist dann auch keine Priorität mehr, denn Notwendiges kann keine sein. |  |
| `head_to_head_title` | Earn its place | Platz verdienen | Watch: "Earn its place" could read as a test; kept, summaries never say won/lost |
| `head_to_head_question` | Which would you rather have? | Was hättest du lieber? |  |
| `head_to_head_not_priority` | Not a priority yet | Noch keine Priorität |  |
| `head_to_head_no_answer` | No answer yet | Noch keine Antwort |  |
| `head_to_head_summary_title` | Your priorities | Deine Prioritäten |  |
| `head_to_head_done` | Done | Fertig |  |
| `head_to_head_gone` | This item can’t become a priority anymore. | Dieser Artikel kann keine Priorität mehr werden. |  |
| `priority_outcome_stays` | %1$s stays #%2$d | %1$s bleibt auf Platz %2$d |  |
| `priority_outcome_now` | %1$s is now #%2$d | %1$s ist jetzt auf Platz %2$d |  |
| `priority_outcome_back` | %1$s moved back to the wishlist; its wait is paused | %1$s ist zurück auf der Wunschliste, die Wartezeit pausiert |  |
| `priority_outcome_stays_on_wishlist` | %1$s stays on the wishlist | %1$s bleibt auf der Wunschliste |  |
| `opportunity_cost_title` | What else this money could do | Was du sonst mit dem Geld machen könntest |  |
| `opportunity_cost_share` | %1$d%% of the price of your %2$s (#%3$d) | %1$d %% des Preises von %2$s (#%3$d) |  |
| `opportunity_cost_share_less` | Less than 1%% of the price of your %1$s (#%2$d) | Weniger als 1 %% des Preises von %1$s (#%2$d) |  |
| `opportunity_cost_months_less` | Less than a month of your fun money | Weniger als ein Monat deines Spaßgelds |  |
| `settings_title` | Settings | Einstellungen |  |
| `settings_fun_money_row` | Monthly amount and adjustments | Monatsbetrag und Anpassungen |  |
| `settings_notifications_title` | Notifications | Benachrichtigungen |  |
| `settings_notifications_blocked` | Notifications are off for this app in the system settings, so none will show. | Benachrichtigungen sind für diese App in den Systemeinstellungen aus, daher erscheinen keine. |  |
| `settings_notifications_channel_off` | Switched off in the system settings | In den Systemeinstellungen ausgeschaltet |  |
| `settings_open_system_settings` | Open system settings | Systemeinstellungen öffnen |  |
| `settings_language_title` | Language | Sprache |  |
| `settings_language_app` | App language | App-Sprache |  |
| `settings_language_current` | English | Deutsch |  |
| `settings_language_follows_system` | Follows the system language | Folgt der Systemsprache |  |
| `settings_about_title` | About | Über die App |  |
| `settings_version` | Version | Version |  |
| `settings_licence` | Source available, free for personal and non-commercial use | Quellcode einsehbar, kostenlos für private und nicht kommerzielle Nutzung |  |
| `settings_licence_name` | PolyForm Noncommercial License 1.0.0 | — |  |
| `settings_source` | Source code | Quellcode |  |
| `settings_source_location` | github.com/LocNgu/before-you-buy | — |  |
| `settings_privacy_title` | Privacy | Datenschutz |  |
| `settings_privacy_body` | Everything stays on your phone: no account, no server, no analytics, no ads. The app has no internet permission, so it can’t send your data anywhere. | Alles bleibt auf deinem Gerät: kein Konto, kein Server, keine Analyse, keine Werbung. Die App hat keine Internetberechtigung und kann deine Daten daher nirgendwohin senden. |  |
| `settings_no_app` | No app on this phone can open this. | Keine App auf diesem Gerät kann das öffnen. |  |
| `countdown_days_left` | %d day left / %d days left | Noch %d Tag / Noch %d Tage |  |
| `duration_days` | %d day / %d days | %d Tag / %d Tage |  |
| `relative_days_ago` | %d day ago / %d days ago | vor %d Tag / vor %d Tagen |  |
| `wait_once_priority` | Waiting period: %d day, once it’s a priority / Waiting period: %d days, once it’s a priority | Wartezeit als Priorität: %d Tag / Wartezeit als Priorität: %d Tage |  |
| `wait_progress_not_started` | Waiting period not started (%d day) / Waiting period not started (%d days) | Wartezeit noch nicht begonnen (%d Tag) / Wartezeit noch nicht begonnen (%d Tage) |  |
| `wait_progress_partly` | %1$d of %2$d day waited / %1$d of %2$d days waited | %1$d von %2$d Tag gewartet / %1$d von %2$d Tagen gewartet |  |
| `quick_add_price_too_many_decimals` | Use at most %d decimal place / Use at most %d decimal places | Höchstens %d Nachkommastelle / Höchstens %d Nachkommastellen |  |
| `item_wanted_for_days` | Wanted for %d day / Wanted for %d days | Seit %d Tag gewünscht / Seit %d Tagen gewünscht |  |
| `decision_wanted_for_days` | You’ve wanted this for %d day / You’ve wanted this for %d days | Du willst das seit %d Tag / Du willst das seit %d Tagen |  |
| `decision_early_days_left` | %d day of the waiting period is left. / %d days of the waiting period are left. | Noch %d Tag Wartezeit. / Noch %d Tage Wartezeit. |  |
| `decision_wait_days` | In %d day / In %d days | In %d Tag / In %d Tagen |  |
| `history_considered_for_days` | Considered for %d day / Considered for %d days | %d Tag überlegt / %d Tage überlegt |  |
| `notification_waiting_over_text` | You’ve wanted %1$s for %2$d day. Your waiting period is over. / You’ve wanted %1$s for %2$d days. Your waiting period is over. | Du willst %1$s seit %2$d Tag. Deine Wartezeit ist vorbei. / Du willst %1$s seit %2$d Tagen. Deine Wartezeit ist vorbei. |  |
| `notification_waiting_over_digest_title` | %d item finished its waiting period / %d items finished their waiting period | Bei %d Artikel ist die Wartezeit vorbei / Bei %d Artikeln ist die Wartezeit vorbei |  |
| `notification_nudge_digest_title` | Take a minute for %d item / Take a minute for %d items | Nimm dir eine Minute für %d Artikel / Nimm dir eine Minute für %d Artikel |  |
| `home_considering_count` | %d item on your wishlist / %d items on your wishlist | %d Artikel auf deiner Wunschliste / %d Artikel auf deiner Wunschliste |  |
| `priority_slots_used` | %1$d of %2$d slot used / %1$d of %2$d slots used | %1$d von %2$d Platz belegt / %1$d von %2$d Plätzen belegt |  |
| `priority_slots_option` | %1$d slot / %1$d slots | %1$d Platz / %1$d Plätze |  |
| `priority_slots_demoted` | %1$d priority goes back to the wishlist: / %1$d priorities go back to the wishlist: | %1$d Priorität geht zurück auf die Wunschliste: / %1$d Prioritäten gehen zurück auf die Wunschliste: |  |
| `opportunity_cost_months` | %1$d month of your fun money / %1$d months of your fun money | %1$d Monat deines Spaßgelds / %1$d Monate deines Spaßgelds |  |
| `opportunity_cost_months_about` | About %1$d month of your fun money / About %1$d months of your fun money | Etwa %1$d Monat deines Spaßgelds / Etwa %1$d Monate deines Spaßgelds |  |
| `opportunity_cost_delays` | Buying this delays your %2$s (#%3$d) by about %1$d month / Buying this delays your %2$s (#%3$d) by about %1$d months | Wenn du das kaufst, verschiebt sich %2$s (#%3$d) um etwa %1$d Monat / Wenn du das kaufst, verschiebt sich %2$s (#%3$d) um etwa %1$d Monate | Watch: "delays" states a fact about a priority, no urgency |
| `opportunity_cost_smaller_items` | Your %1$d cheapest wishlist item comes to %2$s / Your %1$d cheapest wishlist items come to %2$s together | Das %1$d günstigste Ding auf deiner Wunschliste kostet %2$s / Die %1$d günstigsten Dinge auf deiner Wunschliste kosten zusammen %2$s |  |
| `opportunity_cost_all_items` | The %1$d other item on your wishlist comes to %2$s / All %1$d other items on your wishlist come to %2$s together | Das %1$d andere Ding auf deiner Wunschliste kostet %2$s / Alle %1$d anderen Dinge auf deiner Wunschliste kosten zusammen %2$s |  |
