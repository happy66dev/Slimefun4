package io.github.thebusybiscuit.slimefun4.core.services;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.StorageType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;

/**
 * This Service creates a Backup of your Slimefun world data on every server shutdown.
 * 这个服务在每次服务器关闭时，自动把 Slimefun 的世界数据备份成 zip 文件喵~
 * 只有使用 SQLite 存储类型时才会执行备份操作喵~
 *
 * @author TheBusyBiscuit
 *
 */
public class BackupService implements Runnable {

    /**
     * The maximum amount of backups to maintain
     * 最多保留的备份文件数量，超过这个数量会自动清理最旧的备份喵~
     */
    private static final int MAX_BACKUPS = 20;

    /**
     * Our {@link DateTimeFormatter} for formatting file names.
     * 用于将当前时间格式化成备份文件名的格式器，例如 2026-06-15-10-30.zip 喵~
     */
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm", Locale.ROOT);

    /**
     * The directory in which to create the backups
     * 备份文件存放的目录路径，固定为 data-storage/Slimefun/block-backups 喵~
     */
    private final File directory = new File("data-storage/Slimefun/block-backups");

    /**
     * 备份服务的主执行方法，在服务器关闭时被调用喵~
     *
     * 整体思路：
     *   1. 先检查当前数据库存储类型，不是 SQLite 则跳过备份喵~
     *   2. 确认备份目录存在后，检查备份数量是否超出上限，超出则清理旧备份喵~
     *   3. 用当前时间生成新的 zip 备份文件名，创建文件并写入数据库文件内容喵~
     *   边界条件：备份目录不存在时不执行任何操作；zip 文件已存在时跳过创建喵~
     */
    @Override
    public void run() {
        // 获取数据库管理器，用于判断当前使用的存储类型喵~
        var dbManager = Slimefun.getDatabaseManager();
        // 喵~防御：只有玩家档案存储或方块数据存储至少有一个是 SQLite 时才进行备份，否则直接退出喵~
        if (dbManager.getProfileStorageType() != StorageType.SQLITE
                && dbManager.getBlockDataStorageType() != StorageType.SQLITE) {
            return;
        }
        // Make sure that the directory exists.
        // 确认备份目录存在，目录不存在时跳过后续所有操作喵~
        if (directory.exists()) {
            // 获取备份目录下所有文件，转换为可操作的 List 集合喵~
            List<File> backups = Arrays.asList(directory.listFiles());

            // 如果备份文件数量已经超过最大限制，则清理多余的旧备份喵~
            if (backups.size() > MAX_BACKUPS) {
                try {
                    // 调用 purgeBackups 方法删除最旧的多余备份文件喵~
                    purgeBackups(backups);
                } catch (IOException e) {
                    // 删除旧备份失败时记录警告日志，不阻断后续备份流程喵~
                    Slimefun.logger().log(Level.WARNING, "无法删除旧备份文件", e);
                }
            }

            // 用当前时间生成备份 zip 文件名，格式如 2026-06-15-10-30.zip 喵~
            File file = new File(directory, format.format(LocalDateTime.now()) + ".zip");

            // 喵~防御：如果同名备份文件已存在（同一分钟内重复备份），则跳过，避免覆盖或冲突喵~
            if (!file.exists()) {
                try {
                    // 尝试在磁盘上创建新的备份 zip 文件喵~
                    if (file.createNewFile()) {
                        // 创建成功后，打开 ZipOutputStream 往 zip 文件中写入备份内容喵~
                        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(file))) {
                            // 调用 createBackup 把数据库文件压缩写入 zip 流喵~
                            createBackup(output);
                        }

                        // 备份完成，在日志中输出备份文件名告知服务器管理员喵~
                        Slimefun.logger().log(Level.INFO, "已备份 Slimefun 数据至: {0}", file.getName());
                    } else {
                        // 文件创建失败（如磁盘权限不足），记录警告喵~
                        Slimefun.logger().log(Level.WARNING, "无法创建备份文件: {0}", file.getName());
                    }
                } catch (IOException x) {
                    // 备份过程中发生 IO 异常，记录严重级别日志，包含 Slimefun 版本信息方便排查喵~
                    Slimefun.logger()
                            .log(
                                    Level.SEVERE,
                                    x,
                                    () -> "An Exception occurred while creating a backup for Slimefun "
                                            + Slimefun.getVersion());
                }
            }
        }
    }

    /**
     * 根据当前数据库存储类型，将对应的 SQLite 数据库文件写入 zip 输出流喵~
     *
     * 整体思路：
     *   检查玩家档案存储和方块数据存储各自是否为 SQLite，
     *   是则将对应的 .db 文件添加到 zip 压缩包中喵~
     * 输入：ZipOutputStream 输出流，必须非 null 喵~
     * 输出：无返回值，直接向 zip 流写入文件内容喵~
     * 边界条件：output 为 null 时抛出异常；.db 文件不存在时会抛出 IOException 喵~
     *
     * @param output 要写入备份内容的 ZipOutputStream，不能为 null 喵~
     * @throws IOException 文件读写失败时抛出喵~
     */
    private void createBackup(@Nonnull ZipOutputStream output) throws IOException {
        // 喵~防御：output 为 null 时立即抛出 IllegalArgumentException，避免后续空指针崩溃喵~
        Validate.notNull(output, "The Output Stream cannot be null!");

        // 如果玩家档案使用 SQLite 存储，则把 profile.db 文件加入备份 zip 喵~
        if (Slimefun.getDatabaseManager().getProfileStorageType() == StorageType.SQLITE) {
            // 将 data-storage/Slimefun/profile.db 写入 zip 根路径喵~
            addFile(output, new File("data-storage/Slimefun", "profile.db"), "");
        }

        // 如果方块数据使用 SQLite 存储，则把 block-storage.db 文件加入备份 zip 喵~
        if (Slimefun.getDatabaseManager().getBlockDataStorageType() == StorageType.SQLITE) {
            // 将 data-storage/Slimefun/block-storage.db 写入 zip 根路径喵~
            addFile(output, new File("data-storage/Slimefun", "block-storage.db"), "");
        }
    }

    /**
     * 将单个文件按指定路径压缩写入 ZipOutputStream 喵~
     *
     * 整体思路：
     *   创建一个以 path/fileName 为名的 ZipEntry，然后用 4096 字节缓冲区
     *   循环读取源文件内容，分批写入 zip 流，最后关闭当前条目喵~
     * 输入：zip 输出流、要压缩的文件、zip 内的目录路径前缀喵~
     * 输出：无返回值，文件内容已写入 zip 流喵~
     * 边界条件：file 不存在时 FileInputStream 会抛出 IOException 喵~
     *
     * @param output zip 输出流喵~
     * @param file   要压缩的源文件喵~
     * @param path   该文件在 zip 包内的目录路径前缀喵~
     * @throws IOException 文件读写失败时抛出喵~
     */
    private void addFile(ZipOutputStream output, File file, String path) throws IOException {
        // 创建 ZipEntry，表示 zip 内的一个文件条目，路径格式为 path/文件名 喵~
        var entry = new ZipEntry(path + "/" + file.getName());
        // 把这个 zip 条目写入 zip 流，之后的 write 操作都会写进这个条目喵~
        output.putNextEntry(entry);

        // 分配 4096 字节的读取缓冲区，平衡内存占用和读写效率喵~
        byte[] buffer = new byte[4096];
        // 用 try-with-resources 打开源文件的输入流，确保读完后自动关闭不泄漏资源喵~
        try (var input = new FileInputStream(file)) {
            // 记录每次实际读到的字节数喵~
            int length;

            // 循环读取文件内容，每次读取最多 4096 字节，直到文件末尾（read 返回 -1）喵~
            while ((length = input.read(buffer)) > 0) {
                // 把本次读到的 length 字节写入 zip 流喵~
                output.write(buffer, 0, length);
            }
        }
        // 关闭当前 zip 条目，后续可以继续写入下一个条目喵~
        output.closeEntry();
    }

    /**
     * 将一个目录下的所有文件逐一压缩写入 ZipOutputStream 喵~
     *
     * 整体思路：
     *   遍历目录下所有文件（不递归子目录），逐个调用 addFile 写入 zip 喵~
     * 输入：zip 输出流、目录 File 对象、zip 内的路径前缀喵~
     * 输出：无返回值喵~
     * 边界条件：directory.listFiles() 在目录不存在或 IO 错误时可能返回 null，
     *           此时 for-each 会抛出 NullPointerException 喵~
     *
     * @param output    zip 输出流，不能为 null 喵~
     * @param directory 要压缩的源目录，不能为 null 喵~
     * @param zipPath   该目录内文件在 zip 中的路径前缀，不能为 null 喵~
     * @throws IOException 文件读写失败时抛出喵~
     */
    private void addDirectory(@Nonnull ZipOutputStream output, @Nonnull File directory, @Nonnull String zipPath)
            throws IOException {
        // 遍历目录下所有文件，逐个压缩写入 zip 流喵~
        for (File file : directory.listFiles()) {
            // 将当前文件加入 zip 包，路径前缀为 zipPath 喵~
            addFile(output, file, zipPath);
        }
    }

    /**
     * This method will delete old backups.
     * 删除超出最大保留数量的旧备份文件，只保留最新的 MAX_BACKUPS 个喵~
     *
     * 整体思路：
     *   1. 用正则过滤出符合 yyyy-MM-dd-HH-mm 命名格式的备份文件喵~
     *   2. 按时间从新到旧排序（降序）喵~
     *   3. 从末尾（最旧的那批）开始，删除超出 MAX_BACKUPS 数量的文件喵~
     * 输入：备份目录下所有文件的 List 喵~
     * 输出：无返回值，超出部分的旧备份文件已从磁盘删除喵~
     * 边界条件：文件名格式不匹配的会被过滤掉不参与排序和删除喵~
     *
     * @param backups
     *            The {@link List} of all backups
     *            所有备份文件的列表喵~
     *
     * @throws IOException
     *             An {@link IOException} is thrown if a {@link File} could not be deleted
     *             删除文件失败时抛出 IOException 喵~
     */
    private void purgeBackups(@Nonnull List<File> backups) throws IOException {
        // 主人注意：这里对备份列表进行过滤+排序，当备份文件超过20个时会有较多计算，但通常不会超过此量喵~
        var matchedBackup = backups.stream()
                // 过滤出文件名符合 yyyy-MM-dd-HH-mm 格式（不含扩展名）的备份文件喵~
                .filter(f -> f.getName().matches("^\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}$"))
                // 按时间从新到旧排序（time2.compareTo(time1) 实现降序），最新的排在前面喵~
                .sorted((a, b) -> {
                    // 解析文件 a 的文件名（去掉最后4个字符即扩展名）为 LocalDateTime 喵~
                    LocalDateTime time1 = LocalDateTime.parse(
                            a.getName().substring(0, a.getName().length() - 4), format);
                    // 解析文件 b 的文件名为 LocalDateTime 喵~
                    LocalDateTime time2 = LocalDateTime.parse(
                            b.getName().substring(0, b.getName().length() - 4), format);

                    // 降序比较：time2 在前，time1 在后，保证最新的文件排在列表头部喵~
                    return time2.compareTo(time1);
                })
                // 将排序后的 Stream 转换为不可变 List 喵~
                .toList();

        // 从超出部分的最旧文件开始向前删除，i 表示当前要删除的文件位于排序后列表的倒数第 i 位喵~
        // 主人注意：循环从 (matchedBackup.size() - MAX_BACKUPS) 倒数到 1，依次删除最旧的超出备份喵~
        for (int i = matchedBackup.size() - MAX_BACKUPS; i > 0; i--) {
            // 删除排序后列表中第 i 个（即多余的旧备份）文件，使用 NIO Files.delete 抛出详细异常信息喵~
            Files.delete(matchedBackup.get(i).toPath());
        }
    }
}
