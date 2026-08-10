package gg.grounds.gui.demo

import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.dialog.Dialog
import net.minestom.server.dialog.DialogAction
import net.minestom.server.dialog.DialogActionButton
import net.minestom.server.dialog.DialogAfterAction
import net.minestom.server.dialog.DialogBody
import net.minestom.server.dialog.DialogInput
import net.minestom.server.dialog.DialogMetadata
import gg.grounds.gui.theme.screenMarker
import gg.grounds.gui.theme.screenText
import net.minestom.server.entity.Player

/** Namespace for every action this demo sends back, so one listener can route them all. */
private const val NS = "groundsdemo"

private fun body(text: String) =
    DialogBody.PlainMessage(Component.text(text, NamedTextColor.GRAY), 320)

private fun button(label: String, action: DialogAction?, width: Int = 150) =
    DialogActionButton(Component.text(label), Component.empty(), width, action)

/** A button that reports back with the dialog's input values attached. */
private fun submits(label: String, id: String, width: Int = 150) =
    button(label, DialogAction.DynamicCustom(Key.key(NS, id), CompoundBinaryTag.empty()), width)

/** A button that reports back with nothing but its own identity. */
private fun reports(label: String, id: String, width: Int = 150) =
    button(label, DialogAction.Custom(Key.key(NS, id), CompoundBinaryTag.empty()), width)

private fun meta(
    title: String,
    bodies: List<DialogBody> = emptyList(),
    inputs: List<DialogInput> = emptyList(),
    closable: Boolean = true,
) =
    DialogMetadata(
        Component.text(title),
        Component.text(title),
        closable,
        // pause must be paired with an after_action that unpauses; CLOSE is one, NONE is not, and
        // the mismatch is a decode error that makes the client drop the whole dialog.
        true,
        DialogAfterAction.CLOSE,
        bodies,
        inputs,
    )

/**
 * The index: one dialog that opens the others.
 *
 * Dialogs are the opposite trade from a container GUI. They give real keyboard input — text,
 * numbers, toggles, option lists — and a typed payload back, which no chest screen can do without
 * an anvil hack. What they do not give is any control over how they look: no width, no height, no
 * position, no alignment, no background. Every one of them is a vanilla settings screen with your
 * words in it.
 */
fun openDialogIndex(player: Player) {
    player.showDialog(
        Dialog.MultiAction(
            meta(
                "Dialogs",
                listOf(
                    body(
                        "Real input widgets and a typed payload back — the one thing a container " +
                            "GUI cannot do. In exchange you control none of the layout, unless the " +
                            "artwork rides in as markers."
                    )
                ),
            ),
            listOf(
                button("Notice", DialogAction.ShowDialog(notice())),
                button("Confirmation", DialogAction.ShowDialog(confirmation())),
                button("Report form", DialogAction.ShowDialog(form())),
                button("Themed", DialogAction.ShowDialog(themedDialog(DemoTheme.current()))),
            ),
            button("Close", null),
            // Two columns is the default; naming it makes the grid explicit.
            2,
        )
    )
}

private fun notice(): Dialog =
    Dialog.Notice(
        meta(
            "Notice",
            listOf(
                body(
                    "The simplest dialog: a message and one button. Escape runs that same button's " +
                        "action, so there is no path that silently does nothing."
                )
            ),
        ),
        reports("Understood", "notice_ok"),
    )

private fun confirmation(): Dialog =
    Dialog.Confirmation(
        meta(
            "Confirmation",
            listOf(body("Both buttons report back, so the server learns which one was pressed.")),
        ),
        reports("Yes", "confirm_yes"),
        reports("No", "confirm_no"),
    )

/**
 * All four input types on one screen, submitted through the only action that carries them.
 *
 * Seven of the nine action types discard input entirely — including the plainly named `custom`.
 * Only `dynamic/custom` and `dynamic/run_command` see it, which is the single easiest thing to get
 * wrong about dialogs.
 */
private fun form(): Dialog =
    Dialog.MultiAction(
        meta(
            "Report a player",
            listOf(body("Fill this in, then submit. The server echoes back exactly what it received.")),
            listOf(
                DialogInput.Text(
                    "target",
                    200,
                    Component.text("Player"),
                    true,
                    "",
                    16,
                    null,
                ),
                DialogInput.SingleOption(
                    "reason",
                    200,
                    listOf(
                        DialogInput.SingleOption.Option("cheating", Component.text("Cheating"), true),
                        DialogInput.SingleOption.Option("chat", Component.text("Chat abuse"), false),
                        DialogInput.SingleOption.Option("other", Component.text("Something else"), false),
                    ),
                    Component.text("Reason"),
                    true,
                ),
                DialogInput.NumberRange(
                    "severity",
                    200,
                    Component.text("Severity"),
                    "options.generic_value",
                    1f,
                    5f,
                    3f,
                    1f,
                ),
                DialogInput.Boolean("anonymous", Component.text("Report anonymously"), false, "true", "false"),
            ),
        ),
        listOf(submits("Submit", "report_submit")),
        button("Cancel", null),
        1,
    )

/**
 * A dialog with artwork in it, which the pack format on its own cannot do.
 *
 * What a resource pack reaches inside a dialog is almost nothing: three sprites of its own — the
 * warning button in three states — and otherwise the widgets it shares with the whole game.
 * Overriding `widget/button` restyles every button in Minecraft, the pause menu included. Layout,
 * width, position and background are addressable from neither side.
 *
 * Markers get in anyway, because of where they ride. They are glyphs in text, a dialog's body is
 * text, and the shader relocating them never asks which screen the text came from. So the plate,
 * the heading, the rule and the badge below are drawn into a dialog by the same mechanism that
 * draws a hover outline into a container — confirmed on a 26.2 client, not merely expected.
 *
 * Coordinates are offsets from the screen's centre rather than from a window, since a dialog has no
 * window to measure against. The ±128px reach binds harder here than anywhere else: a dialog is
 * wider than a signed byte can span, so the plate is sized to what one marker can place.
 */
fun themedDialog(theme: gg.grounds.gui.theme.Theme): Dialog {
    val art =
        Component.empty()
            .append(theme.screenMarker("dialog_plate", -112, -70))
            .append(theme.screenText(GLYPHS, -96, -56, "Themed dialog"))
            .append(theme.screenMarker("dialog_rule", -96, -45, tint = ACCENT))
            .append(theme.screenMarker("dialog_badge", -96, -34))
            .append(theme.screenText(GLYPHS, -56, -30, "Artwork, in a dialog.", tint = DIM))
            .append(theme.screenText(GLYPHS, -56, -18, "Drawn by markers, not", tint = DIM))
            .append(theme.screenText(GLYPHS, -56, -6, "by the dialog format.", tint = DIM))

    return Dialog.Notice(
        meta(
            "Themed",
            // The body is what carries the markers. Its own text is deliberately blank: the words
            // are drawn as artwork above, so the client has nothing of its own to lay out here.
            listOf(DialogBody.PlainMessage(art, 320)),
        ),
        reports("Close", "themed_ok"),
    )
}

/**
 * The one dialog that is not a demo of dialogs but a part of another screen.
 *
 * A container screen cannot read a keystroke — there is no packet that would carry one. A dialog
 * can, and hands back a typed payload. So the market's search button closes the container, opens
 * this, and the listener reopens the container with whatever was typed. Neither half is new; that
 * they can be pointed at each other is the whole idea.
 *
 * `dynamic/custom` is the action, because it is one of only two that carry the input at all — the
 * plainly named `custom` discards it.
 */
fun marketSearchDialog(current: String): Dialog =
    Dialog.MultiAction(
        meta(
            "Search the market",
            listOf(body("Part of an item's name. Leave it empty to see everything again.")),
            listOf(DialogInput.Text("query", 200, Component.text("Name"), true, current, 32, null)),
        ),
        listOf(submits("Search", MARKET_SEARCH)),
        button("Cancel", null),
        1,
    )

/**
 * Formats what came back.
 *
 * The client flattens every input into one compound keyed by the input's own key, and the types are
 * fixed: text and option ids arrive as strings, booleans as a byte, sliders as a float — the
 * `on_true`/`on_false` strings are ignored on this path.
 */
fun describeSubmission(key: Key, payload: CompoundBinaryTag?): Component {
    val entries =
        payload?.keySet()?.sorted()?.joinToString(", ") { name -> "$name=${payload.get(name)}" }
            ?: ""
    return Component.text()
        .append(Component.text("dialog → ", NamedTextColor.DARK_GRAY))
        .append(Component.text(key.value(), NamedTextColor.AQUA))
        .append(
            if (entries.isEmpty()) {
                Component.text("  (no inputs — this action type discards them)", NamedTextColor.DARK_GRAY)
            } else {
                Component.text("  $entries", NamedTextColor.WHITE)
            }
        )
        .build()
}
