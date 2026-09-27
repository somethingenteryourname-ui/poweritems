# PowerItem

Hold an item, then:

- `/poweritem damage <amount>` - extra damage on hit (2 = one heart)
- `/poweritem mode kill` - every hit kills, ignores totems, no armor damage
- `/poweritem mode pop` - every hit pops their totem (kills if they don't have one), no armor damage
- `/poweritem mode normal` - back to normal hits
- `/poweritem trigger attack` - powers only on normal hits (default)
- `/poweritem trigger use` - powers only on damage from using the item (right-click abilities)
- `/poweritem trigger both` - powers on either
- `/poweritem usetime <seconds>` - how long powers stay active after using the item (default 3)
- `/poweritem info` - see the item's powers
- `/poweritem clear` - remove all powers

Short alias: `/pi`. Ops only by default (permission `poweritem.use`).
