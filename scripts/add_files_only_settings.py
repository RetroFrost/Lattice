from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Let Lattice pre-populate Telegram's existing multi-chat picker.
replace_once(
    "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java",
    """    public void setDelegate(DialogsActivityDelegate dialogsActivityDelegate) {
        delegate = dialogsActivityDelegate;
    }
""",
    """    public void setDelegate(DialogsActivityDelegate dialogsActivityDelegate) {
        delegate = dialogsActivityDelegate;
    }

    public void setLatticePreselectedDialogs(ArrayList<Long> dialogIds) {
        selectedDialogs.clear();
        if (dialogIds != null) {
            selectedDialogs.addAll(dialogIds);
        }
    }
""",
)

privacy = "TMessagesProj/src/main/java/org/telegram/ui/PrivacySettingsActivity.java"
replace_once(
    privacy,
    "import org.telegram.messenger.LocaleController;\n",
    "import org.telegram.messenger.LocaleController;\nimport org.telegram.messenger.LatticeChatPreferences;\n",
)
replace_once(
    privacy,
    "    private int privacyShadowRow;\n",
    "    private int privacyShadowRow;\n    private int latticeFilesOnlyRow;\n",
)
replace_once(
    privacy,
    """            } else if (position == groupsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE));
            } else if (position == callsRow) {
""",
    """            } else if (position == groupsRow) {
                presentFragment(new PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE));
            } else if (position == latticeFilesOnlyRow) {
                presentFragment(new LatticeFilesOnlyActivity());
            } else if (position == callsRow) {
""",
)
replace_once(
    privacy,
    """        groupsRow = rowCount++;
        privacyShadowRow = rowCount++;
""",
    """        groupsRow = rowCount++;
        latticeFilesOnlyRow = rowCount++;
        privacyShadowRow = rowCount++;
""",
)
replace_once(
    privacy,
    """            return position == passcodeRow || position == passwordRow || position == passkeysRow || position == blockedRow || position == sessionsRow || position == secretWebpageRow || position == webSessionsRow ||
""",
    """            return position == latticeFilesOnlyRow || position == passcodeRow || position == passwordRow || position == passkeysRow || position == blockedRow || position == sessionsRow || position == secretWebpageRow || position == webSessionsRow ||
""",
)
replace_once(
    privacy,
    """                    } else if (position == callsRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS)) {
""",
    """                    } else if (position == latticeFilesOnlyRow) {
                        int count = LatticeChatPreferences.getFilesOnlyDialogsCount();
                        value = count == 0 ? "Off" : String.format(LocaleController.getInstance().getCurrentLocale(), "%d selected", count);
                        textCell.setTextAndValue("Files-only chats", value, true);
                    } else if (position == callsRow) {
                        if (getContactsController().getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS)) {
""",
)
replace_once(
    privacy,
    """            if (position == passportRow || position == lastSeenRow || position == phoneNumberRow ||
                    position == deleteAccountRow || position == webSessionsRow || position == groupsRow || position == paymentsClearRow ||
""",
    """            if (position == passportRow || position == lastSeenRow || position == phoneNumberRow ||
                    position == deleteAccountRow || position == webSessionsRow || position == groupsRow || position == latticeFilesOnlyRow || position == paymentsClearRow ||
""",
)

print("Files-only selector integrated into Telegram privacy settings")
