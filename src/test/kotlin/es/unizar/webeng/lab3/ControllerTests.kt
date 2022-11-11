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
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono
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

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ControllerTests {

    @Autowired
    private lateinit var webClient: WebTestClient

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

        webClient.post().uri("/employees")
            .contentType(APPLICATION_JSON)
            .body(BodyInserters.fromPublisher(Mono.just(MANAGER_REQUEST_BODY("Tom")), String::class.java))
            .accept(APPLICATION_JSON)
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentType(APPLICATION_JSON)
            .expectHeader().location("http://localhost/employees/1")
            .expectBody()
            .equals(MANAGER_RESPONSE_BODY("Mary", 1))

        webClient.post().uri("/employees")
            .contentType(APPLICATION_JSON)
            .body(BodyInserters.fromPublisher(Mono.just(MANAGER_REQUEST_BODY("Mary")), String::class.java))
            .accept(APPLICATION_JSON)
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentType(APPLICATION_JSON)
            .expectHeader().location("http://localhost/employees/2")
            .expectBody()
            .equals(MANAGER_RESPONSE_BODY("Mary", 2))

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

        webClient.get().uri("/employees/1")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody()
            .equals(MANAGER_RESPONSE_BODY("Mary", 1))

        webClient.get().uri("/employees/1")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(APPLICATION_JSON)
            .expectBody()
            .equals(MANAGER_RESPONSE_BODY("Mary", 1))

        webClient.get().uri("/employees/2")
            .exchange()
            .expectStatus().isNotFound

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

        webClient.put().uri("/employees/1")
            .contentType(APPLICATION_JSON)
            .body(BodyInserters.fromPublisher(Mono.just(MANAGER_REQUEST_BODY("Tom")), String::class.java))
            .accept(APPLICATION_JSON)
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentType(APPLICATION_JSON)
            .expectHeader().valueEquals("Content-Location", "http://localhost/employees/1")
            .expectBody()
            .equals(MANAGER_RESPONSE_BODY("Tom", 1))

        webClient.put().uri("/employees/1")
            .contentType(APPLICATION_JSON)
            .body(BodyInserters.fromPublisher(Mono.just(MANAGER_REQUEST_BODY("Tom")), String::class.java))
            .accept(APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(APPLICATION_JSON)
            .expectHeader().valueEquals("Content-Location", "http://localhost/employees/1")
            .expectBody()
            .equals(MANAGER_RESPONSE_BODY("Tom", 1))

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

        webClient.delete().uri("/employees/1")
            .exchange()
            .expectStatus().isNoContent

        webClient.delete().uri("/employees/1")
            .exchange()
            .expectStatus().isNotFound

        // VERIFY
        verify(exactly = 1) {
            employeeRepository.deleteById(1)
        }

        verify(exactly = 2) {
            employeeRepository.findById(1)
        }
    }
}
