## Telegram Contest for Android Developers. 2025 round 1.

The task was to rework the existing profile screens by implementing the new design flow in strict accordance with the mockups provided below:
– Profiles of users, businesses, groups, and channels must be covered, including groups with topics enabled.
– Support for both day and night themes is mandatory.
– All related tabs and profile instances must be supported, including those where gifts are displayed.

https://github.com/user-attachments/assets/d467b443-6474-4d47-9ee1-c4ec3f471890

https://github.com/user-attachments/assets/44279e2d-8d6f-4070-82ba-69380d8bf403

[Original post](https://t.me/contest/420)

## The result

Key techniques I used in my implementation:
- Animation of collapsing avatar using **OpenGL ES** with capturing texture of bitmap of profile image.
- Blur mirrored background under the profile photo using native **C++** bitmap processing.
- **Render Node** for blurred background under profile buttons.

https://github.com/user-attachments/assets/27c1ffbe-089b-46db-8d37-9b3f1c0ffc4f

