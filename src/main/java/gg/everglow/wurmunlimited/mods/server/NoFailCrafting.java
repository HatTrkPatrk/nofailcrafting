package gg.everglow.wurmunlimited.mods.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.CreationEntry;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.skills.Skill;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

public class NoFailCrafting implements WurmServerMod, Configurable, PreInitable, ServerStartedListener
{
  private static final String MOD_ID = "NoFailCrafting";
  private static final Logger LOGGER = Logger.getLogger(NoFailCrafting.MOD_ID);

  private static Set<Integer> _CFG_NO_FAIL_CRAFTING = new HashSet<>();

  private static JSONArray _TEMPCFG_NO_FAIL_CRAFTING = null;

  public static boolean isNoFailCrafting(final int objectCreated)
  {
    if (NoFailCrafting._CFG_NO_FAIL_CRAFTING == null) return false;
    return NoFailCrafting._CFG_NO_FAIL_CRAFTING.contains(objectCreated);
  }

  /*
   * Called first in the mod lifecycle.
   * Should be used to prepare mod settings.
   * No code modifications should happen at this stage.
   */
  @Override
  public void configure(final Properties properties)
  {
    final String path = "mods/nofailcrafting.json";
    if (!new File(path).exists())
    {
      LOGGER.warning("Failed to load config JSON " + path + " (reason: file not found)");
      return;
    }

    JSONObject cfg = null;
    try
    {
      cfg = new JSONObject(new JSONTokener(new String(Files.readAllBytes(Paths.get(path)))));
    }
    catch (final IOException | JSONException ex)
    {
      LOGGER.log(Level.WARNING, "Exception encountered while reading JSON config file", ex);
      return;
    }

    final JSONArray noFailCrafting = cfg.optJSONArray("noFailCrafting", null);
    if (noFailCrafting == null)
    {
      LOGGER.log(Level.WARNING, "Error encountered in JSON config (reason: unable to read/parse)");
    }
    else
    {
      NoFailCrafting._TEMPCFG_NO_FAIL_CRAFTING = noFailCrafting;
    }
  }

  /*
   * Called when the server is started.
   */
  @Override
  public void onServerStarted()
  {
    int idx = -1;
    for (final Object obj : NoFailCrafting._TEMPCFG_NO_FAIL_CRAFTING)
    {
      idx++;

      if (obj instanceof String)
      {
        final String itemName = obj.toString();

        try
        {
          final Integer id = ItemList.class.getField(itemName).getInt(null);
          NoFailCrafting._CFG_NO_FAIL_CRAFTING.add(id);
          LOGGER.log(Level.INFO, "Entry added: " + itemName + " (" + id + ")");
          continue;
        }
        catch (final IllegalAccessException | NoSuchFieldException ex)
        {
          LOGGER.log(Level.WARNING, "Skipping invalid entry at index " + idx + " (reason: " + itemName + " not found in ItemList)");
          continue;
        }
      }
      else
      {
        LOGGER.log(Level.WARNING, "Skipping invalid entry at index " + idx + " (reason: not a \"string\" value)");
        continue;
      }
    }
  }

  /*
   * Called after configure and before init.
   * Byte code editing should happen in this phase but no classes should be loaded here which causes the class to be frozen.
   * HookManager#registerHook should NOT be called in this phase. Adding hooks renames methods and may prevent other mods
   * from editing the byte code of that method.
   */
  @Override
  public void preInit()
  {
    try
    {
      final ClassPool classPool = HookManager.getInstance().getClassPool();

      final CtClass ctCreationEntry = classPool.get("com.wurmonline.server.items.CreationEntry");
      ctCreationEntry.getMethod(
        "getDifficultyFor",
        Descriptor.ofMethod(
          CtClass.floatType,
          new CtClass[]
          {
            classPool.get("com.wurmonline.server.items.Item"),
            classPool.get("com.wurmonline.server.items.Item"),
            classPool.get("com.wurmonline.server.creatures.Creature"),
          }
        )
      ).insertBefore(
        "if (gg.everglow.wurmunlimited.mods.server.NoFailCrafting.check($0, $3, $0.objectCreated, $0.primarySkill))\n" +
        "{\n" +
        "  return 100.0f;\n" +
        "}"
      );

      final CtClass ctSimpleCreationEntry = classPool.get("com.wurmonline.server.items.SimpleCreationEntry");
      ctSimpleCreationEntry.getMethod(
        "run",
        Descriptor.ofMethod(
          classPool.get("com.wurmonline.server.items.Item"),
          new CtClass[]
          {
            classPool.get("com.wurmonline.server.creatures.Creature"),
            classPool.get("com.wurmonline.server.items.Item"),
            CtClass.longType,
            CtClass.floatType,
          }
        )
      ).instrument(
        new ExprEditor()
        {
          private int count = 0;
          @Override
          public void edit(final MethodCall mc) throws CannotCompileException
          {
            if ("isTutorialItem".equals(mc.getMethodName()))
            {
              count += 1;
              if (count == 2)
              {
                mc.replace(
                  "{\n" +
                  "  $_ = $proceed($$);\n" +
                  "  if (gg.everglow.wurmunlimited.mods.server.NoFailCrafting.isNoFailCrafting(this.objectCreated))\n" +
                  "  {\n" +
                  "    if (gg.everglow.wurmunlimited.mods.server.NoFailCrafting.check(this, performer, this.objectCreated, this.primarySkill))\n" +
                  "    {\n" +
                  "      $_ = true;\n" +
                  "    }\n" +
                  "  }\n" +
                  "}"
                );
              }
            }
            else if ("nextInt".equals(mc.getMethodName()))
            {
              mc.replace(
                "{\n" +
                "  $_ = $proceed($$);\n" +
                "  if ($1 == 100 && gg.everglow.wurmunlimited.mods.server.NoFailCrafting.isNoFailCrafting(this.objectCreated))\n" +
                "  {\n" +
                "    if (gg.everglow.wurmunlimited.mods.server.NoFailCrafting.check(this, performer, this.objectCreated, this.primarySkill))\n" +
                "    {\n" +
                "      $_ = 0;\n" +
                "    }\n" +
                "  }\n" +
                "}"
              );
            }
          }
        }
      );
    }
    catch (final Exception ex)
    {
      throw new RuntimeException(ex);
    }
  }

  public static boolean check(final CreationEntry ce, final Creature performer, final int objectCreated, final int primarySkill)
  {
    if (NoFailCrafting.isNoFailCrafting(objectCreated))
    {
      try
      {
        final Skill primSkill = performer.getSkills().getSkill(primarySkill);
        if (ce.hasMinimumSkillRequirement() && ce.getMinimumSkillRequirement() > primSkill.getKnowledge(0.0)) return false;
        return true;
      }
      catch (final Exception ex) {}
    }
    return false;
  }
}
