import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {Observable} from a) {
	"rxjs/index"
};
import {environment} from "../../environments/environment";
import {DataTypes} from "../types";

/**
 * Returns the list of data types from the API server.
 */
@Injectable()
export class DataTypesService {

    constructor(private http: HttpClient) {
    }

    getDataTypes(): Observable<DataTypes> {
        return this.http.get(`${environment.apiBaseUrl}/api/datatypes`) as Observable<DataTypes>;
    }

}
