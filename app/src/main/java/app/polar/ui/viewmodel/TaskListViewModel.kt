package app.polar.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import app.polar.data.AppDatabase
import app.polar.data.entity.TaskList
import app.polar.data.repository.TaskRepository
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
  
  fun selectList(listId: Long?) {
    _selectedListId.value = listId
  }
  
  fun insertTaskList(title: String, icon: String = "ic_list") = viewModelScope.launch {
    val taskList = TaskList(title = title, icon = icon)
    repository.insertTaskList(taskList)
  }
  
  fun updateTaskList(taskList: TaskList) = viewModelScope.launch {
    repository.updateTaskList(taskList)
  }
  
  fun deleteTaskList(taskList: TaskList) = viewModelScope.launch {
    repository.deleteTaskList(taskList)
  }
}
