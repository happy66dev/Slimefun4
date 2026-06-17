package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * 当玩家({@link Player})的{@link PlayerProfile}被加载到内存时触发此事件喵~
 * {@link AsyncProfileLoadEvent}是异步调用的，可以在必要时用于"注入"自定义的{@link PlayerProfile}喵~
 *
 * @author TheBusyBiscuit
 *
 * @see PlayerProfile
 *
 */
public class AsyncProfileLoadEvent extends Event {

    // 静态HandlerList实例，用于Bukkit事件系统中注册和调用所有监听该事件的监听器喵
    private static final HandlerList handlers = new HandlerList();

    // 玩家档案的唯一标识符UUID，标记该事件关联的是哪位玩家喵
    private final UUID uniqueId;
    // 玩家的Slimefun档案对象，可在事件处理过程中被替换为自定义实现喵
    private PlayerProfile profile;

    /**
     * 构造一个异步玩家档案加载事件喵~
     *
     * @param profile
     *            被加载到内存的玩家{@link PlayerProfile}实例
     */
    public AsyncProfileLoadEvent(@Nonnull PlayerProfile profile) {
        // 该事件可能因意外在主线程被调用，或发生在数据迁移期间，具体原因尚不明确喵
        // !Bukkit.isPrimaryThread()：若当前不在主线程则为true，事件以异步方式运行喵
        super(!Bukkit.isPrimaryThread());

        // 喵~防御：校验传入的玩家档案不能为null，防止后续操作出现空指针异常崩溃喵
        Validate.notNull(profile, "The Profile cannot be null");

        // 从档案对象中提取玩家UUID并缓存，用于后续校验注入的档案UUID是否匹配喵
        this.uniqueId = profile.getUUID();
        // 保存传入的玩家档案引用到事件实例中，供外部监听器读取喵
        this.profile = profile;
    }

    /**
     * 获取该事件关联的玩家UUID喵~
     *
     * @return 玩家的唯一标识符{@link UUID}
     */
    @Nonnull
    public UUID getPlayerUUID() {
        // 返回该事件关联的玩家UUID，外部监听器可用它来判断是哪个玩家的档案加载事件喵
        return uniqueId;
    }

    /**
     * 获取当前事件中持有的玩家档案喵~
     *
     * @return 被加载的{@link PlayerProfile}，可能已被外部监听器替换为自定义实现喵
     */
    @Nonnull
    public PlayerProfile getProfile() {
        // 返回当前事件持有的玩家档案实例，外部可通过此方法读取或检查档案数据喵
        return profile;
    }

    /**
     * 此方法用于注入自定义的{@link PlayerProfile}实现喵~
     * 注意：传入的{@link PlayerProfile}必须与原始档案拥有相同的{@link UUID}，否则校验会失败喵~
     *
     * @param profile
     *            要注入的{@link PlayerProfile}实例，UUID必须与事件原始的UUID一致喵
     */
    public void setProfile(@Nonnull PlayerProfile profile) {
        // 喵~防御：校验注入的档案不能为null，防止后续操作出现空指针崩溃喵
        Validate.notNull(profile, "The PlayerProfile cannot be null!");
        // 喵~防御：校验注入档案的UUID必须与事件原始的UUID一致，防止被替换为其他玩家的档案导致数据错乱喵
        Validate.isTrue(profile.getUUID().equals(uniqueId), "Cannot inject a PlayerProfile with a different UUID");

        // 校验通过后将事件持有的玩家档案替换为传入的自定义实现喵
        this.profile = profile;
    }

    /**
     * 获取本事件类的静态{@link HandlerList}，供Bukkit事件系统注册监听器时调用喵~
     *
     * @return 本事件对应的静态HandlerList实例
     */
    @Nonnull
    public static HandlerList getHandlerList() {
        // 返回静态HandlerList实例，Bukkit事件系统通过此方法完成监听器注册喵
        return handlers;
    }

    /**
     * 覆写父类的getHandlers方法，将调用委托给静态getHandlerList()喵~
     *
     * @return 本事件的{@link HandlerList}实例
     */
    @Nonnull
    @Override
    public HandlerList getHandlers() {
        // 委托给静态方法getHandlerList()返回HandlerList，Bukkit事件系统内部通过此方法获取监听器列表喵
        return getHandlerList();
    }
}
