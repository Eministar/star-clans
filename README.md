<div align="center">

```
   *   
  ***  
 ***** 
*******
 ***** 
  ***  
   *   
```

# StarClans

**The ultimate clan system for stars.**

GUI-first clans, smart invites, tag styling, clan chat, and PlaceholderAPI support for Spigot/Paper 1.21.

</div>

---

## ✨ Highlights
- **Polished GUIs** for creation, management, invites, members, and settings
- **Clan chat** toggle for focused team comms
- **Tag & suffix styling** for identity and rank flair
- **PlaceholderAPI** expansion for scoreboard/tab/list integrations
- **Vault-ready** for economy features
- **Update checker** with clickable OP chat buttons

## ⚙️ Requirements
- Java 21
- Spigot/Paper 1.21
- Optional: PlaceholderAPI
- Optional: Vault

## 🚀 Install
1. Drop the jar into `plugins/`
2. Start the server once to generate `config.yml`
3. Configure the database in `config.yml`
4. Restart the server

## 🧭 Commands
```
/clan                      - Open main menu
/clan create               - Create a clan
/clan invites              - View invites/requests
/clan invite <player>      - Invite a player
/clan accept <id>          - Accept invite/request
/clan deny <id>            - Deny invite/request
/clan members              - Open members list
/clan members <player>     - Manage a member
/clan manage               - Open manage GUI (leader only)
/clan settings             - Open settings GUI
/clan tagstyler            - Open tag/suffix styling
/clan tagstyle             - Alias for tagstyler
/clan styler               - Alias for tagstyler
/clan chat                 - Toggle clan chat
/clan kick <player>        - Kick a member
/clan promote <player>     - Promote a member
/clan demote <player>      - Demote a member
/clan leave                - Leave clan
/clan disband              - Disband clan (leader only)

/starclans reload          - Reload config/Vault/DB
```

## 🔐 Permissions
```
starclans.admin.reload     - Allows /starclans reload
```

## 🧩 Placeholders (PlaceholderAPI)
Identifier: `starclans`
```
%starclans_name%              - Clan name
%starclans_name_formatted%    - Formatted name with member count
%starclans_tag%               - Clan tag
%starclans_role%              - Role (Leader/Officer/Member)
%starclans_members%           - Member count
%starclans_invites%           - Invite count
%starclans_in_clan%           - true/false
%starclans_suffix%            - Styled suffix [TAG]
%starclans_formatted_suffix%  - Suffix prefixed with space if not empty
```

## 🗄️ Config (example)
```
database:
  enabled: true
  host: "127.0.0.1"
  port: 3306
  name: "starclans"
  username: "root"
  password: "password"
  pool:
    maxPoolSize: 10
    minIdle: 2
    connectionTimeoutMs: 10000
    idleTimeoutMs: 600000
    maxLifetimeMs: 1800000
```

## 🛠️ Build
```
mvn clean package
```

---

## 💫 Credits
- Eministar
