# MappingRemapper
Converts an old version of Bukkit's BuildData into newer version automatically.

## How to use

When using without any arguments, it will try to use following files:
- `./mappings.txt` - the mojang obfuscation map file of old version to load
- `./bukkit-cl.csrg` - the input bukkit-\<version\>-cl.csrg file to process
- `./bukkit-members.csrg` - the input bukkit-\<version\>-members.csrg file to process
- `./bukkit.exclude` - the input bukkit-\<version\>.exclude file to process
- `./mappings-new.txt` - the mojang mapping file of new version to load
- `./output-cl.csrg` - the output cl file path
- `./output-members.csrg` - the output members file path
- `./output.exclude` - the output exclude file path
