package io.github.thebusybiscuit.slimefun4.api;

import city.norain.slimefun4.SlimefunExtended;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;
import org.bukkit.Server;

/**
 * This enum holds all versions of Minecraft that we currently support.
 * // 这个枚举类型定义了Slimefun插件当前支持的所有Minecraft服务器版本喵~
 *
 * @author TheBusyBiscuit
 * @author Walshy
 * @see Slimefun
 */
public enum MinecraftVersion {

    /**
     * This constant represents Minecraft (Java Edition) Version 1.16
     * (The "Nether Update")
     */
    MINECRAFT_1_16(1, 16, "1.16.x"), // Minecraft 1.16 版本常量，代表"下界更新"喵

    /**
     * This constant represents Minecraft (Java Edition) Version 1.17
     * (The "Caves and Cliffs: Part I" Update)
     */
    MINECRAFT_1_17(1, 17, "1.17.x"), // Minecraft 1.17 版本常量，代表"洞穴与山崖：第一部分"更新喵

    /**
     * This constant represents Minecraft (Java Edition) Version 1.18
     * (The "Caves and Cliffs: Part II" Update)
     */
    MINECRAFT_1_18(1, 18, "1.18.x"), // Minecraft 1.18 版本常量，代表"洞穴与山崖：第二部分"更新喵

    /**
     * This constant represents Minecraft (Java Edition) Version 1.19
     * ("The Wild Update")
     */
    MINECRAFT_1_19(1, 19, "1.19.x"), // Minecraft 1.19 版本常量，代表"荒野更新"喵

    /**
     * This constant represents Minecraft (Java Edition) Version 1.20
     * ("The Trails &amp; Tales Update")
     */
    MINECRAFT_1_20(1, 20, "1.20.x"), // Minecraft 1.20 版本常量，代表"足迹与故事"更新喵

    /**
     * This constant represents Minecraft (Java Edition) Version 1.20.5
     * ("The Armored Paws Update")
     */
    MINECRAFT_1_20_5(1, 20, 5, "1.20.5+"), // Minecraft 1.20.5 版本常量，代表"装甲之爪"更新喵

    /**
     * This constant represents Minecraft (Java Edition) Version 1.21
     * ("The Tricky Trials Update")
     */
    MINECRAFT_1_21(1, 21, "1.21.x"), // Minecraft 1.21 版本常量，代表"棘巧试炼"更新喵

    /**
     * This constant represents Minecraft (Java Edition) Version 26.1
     * (Tiny Takeover Update)
     */
    MINECRAFT_26_1(26, 1, "26.1.x"), // Minecraft 26.1 版本常量，代表"迷你接掌"更新喵

    /**
     * This constant represents an exceptional state in which we were unable
     * to identify the Minecraft Version we are using
     */
    UNKNOWN("Unknown", true), // 未知版本常量，表示无法识别当前Minecraft版本的特殊状态喵

    /**
     * This is a very special state that represents the environment being a Unit
     * Test and not an actual running Minecraft Server.
     */
    UNIT_TEST("Unit Test Environment", true); // 单元测试环境常量，表示当前运行在单元测试而非真实Minecraft服务器中喵

    private final String name; // 版本的显示名称，如"1.16.x"喵
    private final boolean virtual; // 是否为虚拟版本：true表示未知/单元测试等非真实Minecraft版本喵
    private final int majorVersion; // Minecraft主版本号，例如1.20中的20喵
    private final int minorVersion; // Minecraft次版本号（在Minecraft版本命名中，对应语义化版本的补丁号）喵
    private final int patchVersion; // Minecraft补丁版本号，仅在1.20.5这样的三段式版本号中使用喵

    /**
     * This constructs a new {@link MinecraftVersion} with the given name.
     * This constructor forces the {@link MinecraftVersion} to be real.
     * It must be a real version of Minecraft.
     * // 两段式版本构造函数：适用于只有major和minor的Minecraft版本（如1.16→major=1, minor=16）喵
     *
     * @param majorVersion The major version of minecraft as an {@link Integer}
     * @param name         The display name of this {@link MinecraftVersion}
     */
    MinecraftVersion(int majorVersion, int minorVersion, @Nonnull String name) {
        this.name = name; // 保存版本显示名称喵~
        this.majorVersion = majorVersion; // 保存Minecraft主版本号喵~
        this.minorVersion = minorVersion; // 保存Minecraft次版本号喵~
        this.patchVersion = -1; // 两段式版本号无补丁版本，设为-1表示此字段未使用喵~
        this.virtual = false; // 标记为真实的Minecraft服务器版本喵~
    }

    /**
     * This constructs a new {@link MinecraftVersion} with the given name.
     * This constructor forces the {@link MinecraftVersion} to be real.
     * It must be a real version of Minecraft.
     * // 三段式版本构造函数：适用于包含补丁号的Minecraft版本（如1.20.5→major=1, minor=20, patch=5）喵
     *
     * @param majorVersion The major (minor in semver, major in MC land) version of minecraft as an {@link Integer}
     * @param minor        The minor (patch in semver, minor in MC land) version of minecraft as an {@link Integer}
     * @param name         The display name of this {@link MinecraftVersion}
     */
    MinecraftVersion(int majorVersion, int minor, int patch, @Nonnull String name) {
        this.name = name; // 保存版本显示名称喵~
        this.majorVersion = majorVersion; // 保存Minecraft主版本号喵~
        this.minorVersion = minor; // 保存Minecraft次版本号喵~
        this.patchVersion = patch; // 保存Minecraft补丁版本号喵~
        this.virtual = false; // 标记为真实的Minecraft服务器版本喵~
    }

    /**
     * This constructs a new {@link MinecraftVersion} with the given name.
     * A virtual {@link MinecraftVersion} (unknown or unit test) is not an actual
     * version of Minecraft but rather a state of the {@link Server} software.
     * // 虚拟版本构造函数：用于UNKNOWN和UNIT_TEST等非真实Minecraft版本的标识喵
     *
     * @param name    The display name of this {@link MinecraftVersion}
     * @param virtual Whether this {@link MinecraftVersion} is virtual
     */
    MinecraftVersion(@Nonnull String name, boolean virtual) {
        this.name = name; // 保存版本显示名称喵~
        this.majorVersion = 0; // 虚拟版本无实际主版本号，设为0占位喵~
        this.minorVersion = -1; // 虚拟版本无实际次版本号，设为-1表示不存在喵~
        this.patchVersion = -1; // 虚拟版本无实际补丁版本号，设为-1表示不存在喵~
        this.virtual = virtual; // 保存是否为虚拟版本的标记喵~
    }

    /**
     * This returns the name of this {@link MinecraftVersion} in a readable format.
     *
     * @return The name of this {@link MinecraftVersion}
     */
    public @Nonnull String getName() {
        return name; // 返回版本显示名称，如"1.16.x"喵
    }

    /**
     * This returns whether this {@link MinecraftVersion} is virtual or not.
     * A virtual {@link MinecraftVersion} does not actually exist but is rather
     * a state of the {@link Server} software used.
     * Virtual {@link MinecraftVersion MinecraftVersions} include "UNKNOWN" and
     * "UNIT TEST".
     *
     * @return Whether this {@link MinecraftVersion} is virtual or not
     */
    public boolean isVirtual() {
        return virtual; // 返回当前版本是否为虚拟版本（未知或单元测试）喵
    }

    /**
     * This tests if the given minecraft version number matches with this
     * {@link MinecraftVersion}.
     * <p>
     * You can compare against live server versions by using {@link SlimefunExtended#isAtLeast(int, int)}.
     * It is equivalent to the "major" version
     * <p>
     * Example: {@literal "1.13"} returns {@literal 13}
     *
     * @param minecraftVersion The {@link Integer} version to match
     * @return Whether this {@link MinecraftVersion} matches the specified version id
     */
    @Deprecated(since = "2026.1")
    public boolean isMinecraftVersion(int minecraftVersion) {
        return this.isMinecraftVersion(minecraftVersion, -1); // 已废弃方法，委托给三段式版本匹配方法，补丁版本传-1表示忽略喵
    }

    /**
     * This tests if the given minecraft version matches with this
     * {@link MinecraftVersion}.
     * <p>
     * You can compare against live server versions by using
     * {@link SlimefunExtended#isAtLeast(int, int)} or {@link SlimefunExtended#isAtLeast(int, int, int)}.
     * <p>
     * Example: {@literal "1.13"} returns {@literal 13}<br />
     * Exampe: {@literal "1.13.2"} returns {@literal 13_2}
     *
     * @param minecraftVersion The {@link Integer} version to match
     * @return Whether this {@link MinecraftVersion} matches the specified version id
     */
    public boolean isMinecraftVersion(int minecraftVersion, int patchVersion) {
        if (isVirtual()) { // 喵~防御：虚拟版本（未知/单元测试）无法匹配任何实际Minecraft版本，直接返回false避免误判喵
            return false; // 虚拟版本不匹配任何实际Minecraft版本喵
        }

        if (this.majorVersion != minecraftVersion) { // 主版本号不匹配时，无需继续检查直接返回false喵
            return false; // 主版本号不同则版本一定不匹配喵
        }
        // 获取当前枚举在values数组中的下一个版本喵
        // 主人注意：虚拟版本（UNKNOWN/UNIT_TEST）定义在枚举最后，所以+1不会引发数组越界喵
        MinecraftVersion nextVersion = values()[this.ordinal() + 1];
        // 检查传入的patchVersion是否在当前版本的合理范围内喵
        // 如果下一个版本是虚拟的、或者下一个版本的主版本号已经变了，说明当前是major内的最新版本喵
        // 否则需要确保patchVersion严格小于下一个版本的minorVersion，避免跨越到下一个版本喵
        return patchVersion >= this.minorVersion
                && (nextVersion.isVirtual()
                        || nextVersion.majorVersion != this.majorVersion
                        || nextVersion.minorVersion > patchVersion);

        // 以下是被注释掉的旧版判断逻辑，保留供后续参考喵
        //        if (this.majorVersion == 20) {
        //            return this.minorVersion == -1 ? patchVersion < 5 : patchVersion >= this.minorVersion;
        //        } else {
        //            return this.minorVersion == -1 || patchVersion >= this.minorVersion;
        //        }
    }

    /**
     * This tests if the given minecraft version matches with this
     * {@link MinecraftVersion}.
     * <p>
     * This method accepts full semantic version numbers.
     * <p>
     * Example: {@literal 26.1.1} is equivalent to {@code major = 26, minor = 1, patch = 1}
     *
     * @param major The major version to match
     * @param minor The minor version to match
     * @param patch The patch version to match
     * @return Whether this {@link MinecraftVersion} matches the specified version
     */
    public boolean isMinecraftVersion(int major, int minor, int patch) {
        if (isVirtual()) { // 喵~防御：虚拟版本无法匹配任何实际Minecraft版本，直接返回false避免误判喵
            return false; // 虚拟版本不匹配任何实际Minecraft版本喵
        }

        if (this.majorVersion != major || this.minorVersion != minor) { // 主版本号或次版本号任一不匹配时直接返回false喵
            return false; // 主版本号或次版本号不同则版本一定不匹配喵
        }

        // 获取当前枚举在values数组中的下一个版本喵
        // 主人注意：虚拟版本定义在枚举最后，所以+1不会引发数组越界喵
        MinecraftVersion nextVersion = values()[this.ordinal() + 1];
        // 检查传入的patch是否在当前版本的合理范围内喵
        // 如果下一个版本是虚拟的、或者主/次版本号变了，说明当前是该段范围内的最新版本喵
        // 否则需要确保patch严格小于下一个版本的patchVersion喵
        return patch >= this.patchVersion
                && (nextVersion.isVirtual()
                        || nextVersion.majorVersion != this.majorVersion
                        || nextVersion.minorVersion != this.minorVersion
                        || nextVersion.patchVersion > patch);
    }

    /**
     * This method checks whether this {@link MinecraftVersion} is newer or equal to
     * the given {@link MinecraftVersion},
     *
     * An unknown version will default to {@literal false}.
     *
     * @param version The {@link MinecraftVersion} to compare
     * @return Whether this {@link MinecraftVersion} is newer or equal to the given {@link MinecraftVersion}
     */
    public boolean isAtLeast(@Nonnull MinecraftVersion version) {
        Validate.notNull(version, "A Minecraft version cannot be null!"); // 喵~防御：确保传入的版本对象不为空，避免后续空指针异常喵

        if (this == UNKNOWN) { // 喵~防御：当前版本为未知时无法做版本高低判断，默认返回false喵
            return false; // 未知版本默认不高于任何已知版本喵
        }

        /**
         * Unit-Test only code.
         * Running #isAtLeast(...) should always be meaningful.
         * If the provided version equals the lowest supported version, then
         * this will essentially always return true and result in a tautology.
         * This is most definitely an oversight from us and should be fixed, therefore
         * we will trigger an exception.
         *
         * In order to not disrupt server operations, this exception is only thrown during
         * unit tests since the oversight itself will be harmless.
         * // 单元测试专用检查代码：如果当前是UNIT_TEST且被比较的版本是枚举中最小的
         * // 说明存在"总是返回true"的冗余比较逻辑，抛异常提醒开发者修复喵
         */
        if (this == UNIT_TEST && version.ordinal() == 0) { // 喵~防御：在单元测试中检测到与最低支持版本的无意义比较喵
            throw new IllegalArgumentException(
                    "Version " + version + " is the lowest supported version already!"); // 抛出异常提醒开发者存在代码中的版本比较逻辑错误喵
        }

        return this.ordinal() >= version.ordinal(); // 通过枚举声明顺序（ordinal）比较版本高低：ordinal越大代表版本越新喵
    }

    /**
     * 检查当前Minecraft版本是否不低于（大于或等于）指定的主版本号和次版本号喵
     *
     * @param majorVersion 要比较的Minecraft主版本号
     * @param minorVersion 要比较的Minecraft次版本号
     * @return 当前版本是否不低于指定版本喵
     */
    public boolean isAtLeast(int majorVersion, int minorVersion) {
        if (this == UNKNOWN) { // 喵~防御：未知版本无法判断版本高低，直接返回false喵
            return false; // 未知版本默认不高于任何版本喵
        }

        return SlimefunExtended.isAtLeast(majorVersion, minorVersion); // 委托给SlimefunExtended工具类进行实际版本比较喵
    }

    /**
     * This checks whether this {@link MinecraftVersion} is older than the specified {@link MinecraftVersion}.
     *
     * An unknown version will default to {@literal true}.
     *
     * @param version The {@link MinecraftVersion} to compare
     * @return Whether this {@link MinecraftVersion} is older than the given one
     */
    public boolean isBefore(@Nonnull MinecraftVersion version) {
        return !isAtLeast(version); // 通过取反isAtLeast的返回值来判断是否更旧：不高于等于即意味着早于喵
        // 以下是被注释掉的旧版直接判断逻辑，保留供参考喵
        //        Validate.notNull(version, "A Minecraft version cannot be null!");
        //
        //        if (this == UNKNOWN) {
        //            return true;
        //        }
        //
        //        return version.ordinal() > this.ordinal();
    }

    /**
     * 检查当前Minecraft版本是否早于（低于）指定的主版本号和次版本号喵
     *
     * @param majorVersion 要比较的Minecraft主版本号
     * @param minorVersion 要比较的Minecraft次版本号
     * @return 当前版本是否早于指定版本喵
     */
    public boolean isBefore(int majorVersion, int minorVersion) {
        return !isAtLeast(majorVersion, minorVersion); // 通过取反isAtLeast的返回值来判断是否更旧：不高于等于即意味着早于喵
    }
}
