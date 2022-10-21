package es.unizar.webeng.lab3

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.*

private val MANAGER_REQUEST_BODY = { name: String ->
    """
    { 
        "role": "Manager", 
        "name": "$name" 
    }
    """
}

private val MANAGER_RESPONSE_BODY = { name: String, id: Int ->
    """
    { 
       "name" : "$name",
       "role" : "Manager",
       "id" : $id
    }
    """
}

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ControllerTests {

    @Autowired
    private lateinit var mvc: MockMvc

    @MockkBean
    private lateinit var employeeRepository: EmployeeRepository

    @Test
    fun `POST is not safe and not idempotent`() {

        // SETUP
        val employee = slot<Employee>()
        every {
            employeeRepository.save(capture(employee))
        } answers {
            employee.captured.copy(id = 1)
        } andThenAnswer {
            employee.captured.copy(id = 2)
        }

        mvc.post("/employees") {
            contentType = MediaType.APPLICATION_JSON
            content = MANAGER_REQUEST_BODY("Mary")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isCreated() }
            header { string("Location", "http://localhost/employees/1") }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.post("/employees") {
            contentType = MediaType.APPLICATION_JSON
            content = MANAGER_REQUEST_BODY("Mary")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isCreated() }
            header { string("Location", "http://localhost/employees/2") }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 2))
            }
        }

        // VERIFY
    }

    @Test
    fun `GET is safe and idempotent`() {

        // SETUP
        every {
            employeeRepository.findById(1)
        } answers {
            Optional.of(Employee("Mary", "Manager", 1))
        }

        every {
            employeeRepository.findById(2)
        } answers {
            Optional.empty()
        }

        mvc.get("/employees/1").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.get("/employees/1").andExpect {
            status { isOk() }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Mary", 1))
            }
        }

        mvc.get("/employees/2").andExpect {
            status { isNotFound() }
        }

        // VERIFY
        verify(exactly = 2) {
            employeeRepository.findById(1)
        }
        verify(exactly = 1) {
            employeeRepository.findById(2)
        }

        // verify method safety
        verify(exactly = 0) {
            employeeRepository.save(any())
            employeeRepository.deleteById(any())
        }
    }

    @Test
    fun `PUT is idempotent but not safe`() {

        // SETUP
        every {
            employeeRepository.findById(1)
        } answers {
            Optional.empty()
        } andThenAnswer {
            Optional.of(Employee("Tom", "Manage", 1))
        }

        val employee = slot<Employee>()
        every {
            employeeRepository.save(capture(employee))
        } answers {
            employee.captured
        }

        mvc.put("/employees/1") {
            contentType = MediaType.APPLICATION_JSON
            content = MANAGER_REQUEST_BODY("Tom")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isCreated() }
            header { string("Content-Location", "http://localhost/employees/1") }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Tom", 1))
            }
        }

        mvc.put("/employees/1") {
            contentType = MediaType.APPLICATION_JSON
            content = MANAGER_REQUEST_BODY("Tom")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            header { string("Content-Location", "http://localhost/employees/1") }
            content {
                contentType(MediaType.APPLICATION_JSON)
                json(MANAGER_RESPONSE_BODY("Tom", 1))
            }
        }

        // VERIFY
        verify(exactly = 2) {
            employeeRepository.findById(1)
        }

        verify(exactly = 2) {
            employeeRepository.save(Employee("Tom", "Manager", 1))
        }
    }

    @Test
    fun `DELETE is idempotent but not safe`() {

        // SETUP

        every {
            employeeRepository.findById(1)
        } answers {
            Optional.of(Employee("Tom", "Manager", 1))
        } andThenAnswer {
            Optional.empty()
        }

        justRun {
            employeeRepository.deleteById(1)
        }

        mvc.delete("/employees/1").andExpect {
            status { isNoContent() }
        }

        mvc.delete("/employees/1").andExpect {
            status { isNotFound() }
        }

        // VERIFY
        verify(exactly = 1) {
            employeeRepository.deleteById(1)
        }

        verify(exactly = 2) {
            employeeRepository.findById(1)
        }
    }
}
