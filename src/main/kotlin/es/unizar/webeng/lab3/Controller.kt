package es.unizar.webeng.lab3

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
class EmployeeController(
    private val repository: EmployeeRepository
) {

    @GetMapping("/employees")
    fun all(): Iterable<Employee> = repository.findAll()

    @PostMapping("/employees")
    fun newEmployee(@RequestBody newEmployee: Employee): ResponseEntity<Employee> {
        val employee = repository.save(newEmployee)

        // Crea URI absoluta utilizando como host y puerto el del servidor que lo ejecuta
        val location = ServletUriComponentsBuilder
            .fromCurrentServletMapping()
            .path("/employees/{id}")
            .build(employee.id)
        return ResponseEntity.created(location).body(employee)
    }

    @GetMapping("/employees/{id}")
    fun one(@PathVariable id: Long): Employee = repository.findById(id).orElseThrow { EmployeeNotFoundException(id) }

    @PutMapping("/employees/{id}")
    fun replaceEmployee(@RequestBody newEmployee: Employee, @PathVariable id: Long): ResponseEntity<Employee> {
        val location = ServletUriComponentsBuilder
            .fromCurrentServletMapping()
            .path("/employees/{id}")
            .build(id)
            .toASCIIString()
        val (status, body) = repository.findById(id)
            .map { employee ->
                employee.name = newEmployee.name
                employee.role = newEmployee.role
                repository.save(employee)
                HttpStatus.OK to employee
            }.orElseGet {
                newEmployee.id = id
                repository.save(newEmployee)
                HttpStatus.CREATED to newEmployee
            }

        // Apoyo para ayudar al navegador para que sepa a qué lugar tiene que hacer la petición
        return ResponseEntity.status(status).header("Content-Location", location).body(body)
    }

    @DeleteMapping("/employees/{id}")
    fun deleteEmployee(@PathVariable id: Long): ResponseEntity<Employee> = repository.findById(id).map {
        repository.deleteById(id)
        ResponseEntity.noContent().build<Employee>()
    }.orElseThrow { EmployeeNotFoundException(id) }
}

@ResponseStatus(HttpStatus.NOT_FOUND)
class EmployeeNotFoundException(id: Long) : Exception("Could not find employee $id")
