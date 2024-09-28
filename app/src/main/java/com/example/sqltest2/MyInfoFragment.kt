package com.example.sqltest2

import okhttp3.*
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide  // 引入 Glide
import com.example.sqltest2.MyMessagesAdapter
import com.example.sqltest2.R
import com.example.sqltest2.api.ApiMyListService
import com.example.sqltest2.api.ApiUploadService
import com.example.sqltest2.api.ApiUserService
import com.example.sqltest2.models.MyMessages
import com.example.sqltest2.utils.JWTStorageHelper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

class MyInfoFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var myMessagesAdapter: MyMessagesAdapter
    private val myMessagesList = ArrayList<MyMessages>()
    private lateinit var profileImage: ImageView
    private val PICK_IMAGE_REQUEST = 1  // 用于区分相册选择结果
    private lateinit var selectedImageFile: File

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_my_info_fragment, container, false)

        profileImage = view.findViewById(R.id.profileImage)
        loadUserProfileImage()

        recyclerView = view.findViewById(R.id.MyAllInfo)
        recyclerView.layoutManager = LinearLayoutManager(context)

        myMessagesAdapter = MyMessagesAdapter(myMessagesList) {
            // 退出按钮点击事件
            JWTStorageHelper.removeJwtToken(requireContext())  // 调用全局方法
            startActivity(Intent(requireContext(), LoginActivity::class.java))  // 跳转到登录界面
        }
        recyclerView.adapter = myMessagesAdapter

        loadMyMessages()  // 调用接口获取消息

        // 为 profileImage 添加点击事件
        profileImage.setOnClickListener {
            openImageChooser()
        }

        return view
    }


    private fun loadUserProfileImage() {
        GlobalScope.launch(Dispatchers.IO) {
            val (user, errorMessage) = ApiUserService.getUserInfo(requireContext())

            withContext(Dispatchers.Main) {
                if (user != null) {
                    val imageUrl = user.userPic

                    Glide.with(this@MyInfoFragment)
                        .load(imageUrl)
                        .placeholder(R.drawable.loading)
                        .error(R.drawable.error)
                        .circleCrop()
                        .into(profileImage)
                } else {
                    Toast.makeText(requireContext(), errorMessage ?: "加载用户信息失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMyMessages() {
        GlobalScope.launch(Dispatchers.IO) {
            val (messages, errorMessage) = ApiMyListService.getMessages(requireContext())

            withContext(Dispatchers.Main) {
                if (messages != null) {
                    myMessagesList.clear()
                    // 将 Message 转换为 MyMessages
                    messages.forEach { message ->
                        myMessagesList.add(MyMessages(message.myId, message.centent))
                    }
                    myMessagesAdapter.notifyDataSetChanged()
                    Toast.makeText(context, "信息加载成功！", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), errorMessage ?: "加载消息失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 打开系统相册
    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // 处理相册选择结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val selectedImageUri: Uri? = data.data
            if (selectedImageUri != null) {
                // 将Uri转换为File
                val imageFile = uriToFile(requireContext(), selectedImageUri)
                selectedImageFile = imageFile

                // 显示用户选择的图片
                Glide.with(this)
                    .load(selectedImageUri)
                    .circleCrop()
                    .into(profileImage)

                // 上传图片
                uploadImage(imageFile)
            }
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)!!
        val file = File(context.cacheDir, "selected_image.jpg")
        file.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream, 8192)
        }
        return file
    }

    private fun uploadImage(file: File) {
        try {
            ApiUploadService.uploadImage(file, requireContext()) { url ->
                GlobalScope.launch(Dispatchers.Main) {
                    if (url != null) {
                        Toast.makeText(requireContext(), "上传成功: $url", Toast.LENGTH_LONG).show()
                        Glide.with(this@MyInfoFragment)
                            .load(url)
                            .placeholder(R.drawable.loading)
                            .error(R.drawable.error)
                            .circleCrop()
                            .into(profileImage)

                        // 将url通过后端patch来更新用户头像
                        updateUserImage(requireContext(), url)

                    } else {
                        Toast.makeText(requireContext(), "上传失败，请检查网络和文件路径", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UploadService", "上传过程中发生错误", e)
            Toast.makeText(requireContext(), "上传错误: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 更新图片
    fun updateUserImage(context: Context, avatarUrl: String) {
        // 使用协程在后台线程中执行网络请求
        CoroutineScope(Dispatchers.IO).launch {
            val response = ApiUploadService.updateUserImage(context, avatarUrl)

            // 在主线程中更新UI
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    Log.d(TAG, "User image updated successfully")
                } else {
                    Log.e(TAG, "Failed to update user image: ${response.code} - ${response.message}")
                }
            }
        }
    }


}
