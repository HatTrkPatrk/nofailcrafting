# NoFailCrafting
Simple Wurm Unlimited 1.9.1.5 server mod that allows a configurable list of crafting items with guaranteed success chance.
This mod only changes the crafting success chance to 100%. It does not alter anything to do with skill requirements.

Currently the mod only works for "simple" items - that is, items that are completely crafted by using one item on
another item, such as planks, shafts, brazier stands, etc.
Support for items that require "continuation" such as carts, chests, etc. may be added in the future when I have time.

# Installation
Save the release ZIP to your Wurm Unlimited server directory and unzip the contents into its top level.
Files will be placed into the "mods" directory:
- mods/
  - nofailcrafting/
    - nofailcrafting.jar
  - nofailcrafting.json
  - nofailcrafting.properties

# Configuration
`nofailcrafting.json` is the configuration file, containing an array of item names that will never fail to craft.
By default, the list contains the items that Wurm Online specified in 2020
https://forum.wurmonline.com/index.php?/topic/174099-patch-notes-03mar20/

Item names are exactly as found in the `ItemList` class - use this as a reference:
https://gist.github.com/HatTrkPatrk/3639c616876c1d3eb4eb1fbdcb5fab8d

As an example, if you want to make wooden pegs a guaranteed success chance, add `"pegWood"` to your configuration.

# Usage
Feel free to use this mod on your server, no need for credit.
Feel free to take the source code and do whatever you want with it too.
