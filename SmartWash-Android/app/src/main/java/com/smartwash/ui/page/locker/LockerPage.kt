package com.smartwash.ui.page.locker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockerPage() {
    var pickupCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LazyColumn {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "寄存柜", fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            items(7) {       // Locker Status Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "选择位置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Status Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatusLegendItem(color = Color(0xFF4CAF50), text = "可投递")
                        StatusLegendItem(color = Color(0xFF2196F3), text = "可取件")
                        StatusLegendItem(color = Color(0xFFF44336), text = "使用中")
                        StatusLegendItem(color = Color(0xFF9E9E9E), text = "维护中")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Locker Grid
                    val lockers = listOf(
                        LockerInfo("A-01", LockerStatus.AVAILABLE),
                        LockerInfo("A-02", LockerStatus.PICKABLE),
                        LockerInfo("A-03", LockerStatus.IN_USE),
                        LockerInfo("A-04", LockerStatus.MAINTENANCE)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        lockers.forEach { locker ->
                            LockerItem(locker)
                        }
                    }
                }
            }

            item {    // Pickup Code Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "取件码",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(6) {
                            OutlinedTextField(
                                value = if (pickupCode.length > it) pickupCode[it].toString() else "",
                                onValueChange = { /* Handled in parent */ },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                singleLine = true,
                                enabled = false
                            )
                        }
                    }

                    Text(
                        text = "请输入短信收到的6位取件码",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item { // Pending Orders Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "待取件订单",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("订单号: 2024020501")
                                Text("柜号: A-02")
                            }
                            Text(
                                text = "清洗完成",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Text(
                                text = "请在24小时内取件", color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item { // Bottom Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {}, modifier = Modifier.weight(1f)
                    ) {
                        Text("扫码取件")
                    }
                    Button(
                        onClick = { }, modifier = Modifier.weight(1f)
                    ) {
                        Text("投递衣物")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun LockerItem(locker: LockerInfo) {
    val (backgroundColor, text) = when (locker.status) {
        LockerStatus.AVAILABLE -> Color(0xFF4CAF50) to "可投递"
        LockerStatus.PICKABLE -> Color(0xFF2196F3) to "可取件"
        LockerStatus.IN_USE -> Color(0xFFF44336) to "使用中"
        LockerStatus.MAINTENANCE -> Color(0xFF9E9E9E) to "维护中"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .size(80.dp)
                .background(backgroundColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = backgroundColor.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = locker.id, color = backgroundColor, fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

enum class LockerStatus {
    AVAILABLE, PICKABLE, IN_USE, MAINTENANCE
}

data class LockerInfo(
    val id: String, val status: LockerStatus
)

data class PickupOrder(
    val orderId: String, val lockerId: String, val timeLimit: String
)
