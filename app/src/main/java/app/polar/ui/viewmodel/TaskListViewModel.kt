package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.polar.data.AppDatabase
import app.polar.data.entity.TaskList
import app.polar.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TaskListViewModel @Inject constructor(
    application: Application,
    private val repository: TaskRepository
) : AndroidViewModel(application) {
  val allTaskLists: LiveData<List<TaskList>> = repository.allTaskLists
  
  private val _selectedListId = MutableLiveData<Long?>()
  val selectedListId: LiveData<Long?> = _selectedListId
  
  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  fun clearError() { _errorMessage.value = null }

  private fun safeLaunch(block: suspend () -> Unit) = viewModelScope.launch {
      try {
          block()
      } catch (e: Exception) {
          e.printStackTrace()
          _errorMessage.value = "Error: ${e.message}"
      }
  }
  
  fun selectList(listId: Long?) {
    _selectedListId.value = listId
  }
  
  fun insertTaskList(title: String, icon: String = "ic_list", isDependencyChain: Boolean = false, color: String = "#7F52FF") = safeLaunch {
    val taskList = TaskList(title = title, icon = icon, isDependencyChain = isDependencyChain, color = color)
    repository.insertTaskList(taskList)
  }
  
  fun updateTaskList(taskList: TaskList) = safeLaunch {
    repository.updateTaskList(taskList)
  }
  
  fun deleteTaskList(taskList: TaskList) = safeLaunch {
    repository.deleteTaskList(taskList)
  }
}
