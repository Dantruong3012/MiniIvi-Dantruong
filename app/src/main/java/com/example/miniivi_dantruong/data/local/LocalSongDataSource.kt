package com.example.miniivi_dantruong.data.local

import com.example.miniivi_dantruong.model.Song
import javax.inject.Inject

class LocalSongDataSource @Inject constructor() {
    fun getMockSongs(): List<Song> {
        return listOf(
            Song(1, "Chiếc khăn gió ấm", "Khánh Phương", "Single", 272),
            Song(2, "Kiếp đỏ đen", "Duy Mạnh", "Single", 240),
            Song(3, "Nơi tình yêu bắt đầu", "Bằng Kiều", "Single", 212),
            Song(4, "Âm thầm bên em", "Sơn Tùng M-TP", "Single", 232),
            Song(5, "Chắc ai đó sẽ về", "Sơn Tùng M-TP", "Single", 180),
            Song(6, "Vợ người ta", "Phan Mạnh Quỳnh", "Single", 209),
            Song(7, "Em của ngày hôm qua", "Sơn Tùng M-TP", "Single", 200),
            Song(8, "Gửi anh xa nhớ", "Bích Phương", "Single", 165),
            Song(9, "Có em chờ", "Min", "Single", 280),
            Song(10, "Nắng ấm xa dần", "Sơn Tùng M-TP", "Single", 290),
            Song(11, "Bốn chữ lắm", "Trúc Nhân", "Single", 270),
            Song(12, "Sau tất cả", "Erik", "Single", 300)
        )
    }
}