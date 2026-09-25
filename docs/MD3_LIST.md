The video demonstrates a classic Material Design pattern: a list with **Multi-Selection via Contextual Action Mode** and **Swipe-to-Action** functionality.

Here is the breakdown of the UX interactions shown in the video:

* **Long-Press Activation:** Long-pressing a list item activates multi-selection mode.


* **Contextual Top Bar:** The standard app bar is replaced by a Contextual Action Bar (CAB) displaying a "Close" (X) button, the count of selected items, and context-specific actions (e.g., pin, archive, delete).


* **Visual State Changes:** Selected items change their background color, and the sender's avatar transitions into a checkmark icon.


* **Tap to Toggle:** Once in selection mode, tapping other items adds or removes them from the selection, updating the count dynamically.


* **Cancellation:** Tapping the "X" in the top bar clears the selection and restores the standard view.


* **Swipe Actions:** Swiping an item horizontally reveals a hidden background action (like an Archive icon).



For modern Android development, **Jetpack Compose** is the recommended way to implement this. Here is the step-by-step architectural approach to replicate this UI/UX using Compose.

### 1. State Management

The core of this UX is tracking which items are currently selected. You should hoist this state to your ViewModel or a parent Composable.

```kotlin
// In your ViewModel or Stateful Composable
var selectedMessageIds by remember { mutableStateOf(emptySet<String>()) }
val isSelectionMode = selectedMessageIds.isNotEmpty()

fun toggleSelection(messageId: String) {
    selectedMessageIds = if (selectedMessageIds.contains(messageId)) {
        selectedMessageIds - messageId
    } else {
        selectedMessageIds + messageId
    }
}

fun clearSelection() {
    selectedMessageIds = emptySet()
}

```

### 2. The Dynamic Top App Bar

Use conditional rendering to swap between your standard `TopAppBar` and the Contextual Action Bar based on whether any items are selected.

```kotlin
@Composable
fun MainAppBar(
    selectedIds: Set<String>,
    onClearSelection: () -> Unit
) {
    if (selectedIds.isNotEmpty()) {
        TopAppBar(
            title = { Text(selectedIds.size.toString()) },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                }
            },
            actions = {
                IconButton(onClick = { /* Handle Pin */ }) { Icon(Icons.Default.PushPin, "") }
                IconButton(onClick = { /* Handle Archive */ }) { Icon(Icons.Default.Archive, "") }
                IconButton(onClick = { /* Handle Delete */ }) { Icon(Icons.Default.Delete, "") }
            },
            // Use a slightly different background color for the contextual bar
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    } else {
        // Your standard search bar / profile picture TopAppBar goes here
        TopAppBar(
            title = { Text("Messages") }
        )
    }
}

```

### 3. The Selectable List Item

To handle both regular taps and long presses, use the `combinedClickable` modifier. For the smooth transition from the avatar to the checkmark, use `Crossfade` or `AnimatedContent`.

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: Message,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onOpenMessage: () -> Unit
) {
    // Background color changes if selected
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
        else 
            MaterialTheme.colorScheme.surface
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection() // If already selecting, tap toggles selection
                    } else {
                        onOpenMessage() // Otherwise, open the message
                    }
                },
                onLongClick = {
                    onToggleSelection() // Long press triggers selection
                }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Crossfade handles the smooth animation from Avatar to Checkmark
        Crossfade(targetState = isSelected, label = "avatar_animation") { selected ->
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                // Replace with your actual Avatar component (e.g., Coil AsyncImage)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Message Content (Sender, Text, Time)
        Column {
            Text(text = message.sender, fontWeight = FontWeight.Bold)
            Text(text = message.snippet, maxLines = 1)
        }
    }
}

```

### 4. Swipe-to-Action (Archive)

To replicate the swipe functionality seen at the end of the clip, wrap your `MessageItem` in a `SwipeToDismissBox` (available in Material 3).

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableMessageItem( /* params */ ) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.StartToEnd || 
                dismissValue == SwipeToDismissBoxValue.EndToStart) {
                // Trigger Archive action here
                true 
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // The background revealed when swiping (e.g., Green box with Archive icon)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Gray)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Archive", tint = Color.White)
            }
        }
    ) {
        // Place the MessageItem component created in Step 3 here
        MessageItem( /* params */ )
    }
}

```

### Alternative: XML / View-Based Architecture

If your project uses traditional Android Views instead of Jetpack Compose, you will need to rely on the following components:

* **Multi-Selection:** Track selected states within your `RecyclerView.Adapter`.
* **Contextual Top Bar:** Implement `ActionMode.Callback` and trigger it using `startSupportActionMode()` when the user long-presses an item. This automatically overlays the standard `Toolbar` with the selection actions.
* **Swipe Actions:** Attach an `ItemTouchHelper` with a `SimpleCallback` to your `RecyclerView` and override `onChildDraw` to paint the background and icon when the user swipes.